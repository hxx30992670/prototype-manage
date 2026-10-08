package com.company.prototype.api.preview;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.ticket.ContentTicketRecord;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class PreviewTicketService {

    private static final Logger log = LoggerFactory.getLogger(PreviewTicketService.class);
    private static final Duration TICKET_TTL = Duration.ofMinutes(120);

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final StringRedisTemplate redisTemplate;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final String previewBaseUrl;

    public record PreviewTicketResponse(String ticket, String contentUrl, Instant expiresAt) {}

    public PreviewTicketService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeAuthorizationService authorizationService,
        StringRedisTemplate redisTemplate,
        PublicIdGenerator idGenerator,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
        @Value("${preview.base-url:http://preview.corp.test}") String previewBaseUrl
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.authorizationService = authorizationService;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.previewBaseUrl = previewBaseUrl.endsWith("/") ? previewBaseUrl.substring(0, previewBaseUrl.length() - 1) : previewBaseUrl;
    }

    @Transactional(readOnly = true)
    public PreviewTicketResponse issueTicketForCurrent(CurrentUser user, String prototypePublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.getCurrentVersionId() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "原型暂无生效发布的版本");
        }

        PrototypeVersionEntity version = versionRepository.findById(proto.getCurrentVersionId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "生效版本不存在"));

        return issueTicket(user, proto, version);
    }

    @Transactional(readOnly = true)
    public PreviewTicketResponse issueTicketForVersion(CurrentUser user, String prototypePublicId, String versionPublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        return issueTicket(user, proto, version);
    }

    private PreviewTicketResponse issueTicket(CurrentUser user, PrototypeEntity proto, PrototypeVersionEntity version) {
        if (version.getStatus() != VersionStatus.PUBLISHED) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "该版本尚未发布完成");
        }

        String rawTicket = UUID.randomUUID().toString().replace("-", "") + idGenerator.nextId();
        String digest = hashTicket(rawTicket);

        Instant expiresAt = Instant.now().plus(TICKET_TTL);
        String entryPath = version.getEntryPath() != null ? version.getEntryPath() : "index.html";

        ContentTicketRecord record = new ContentTicketRecord(
            digest,
            "INTERNAL",
            user.id().toString(),
            version.getId(),
            version.getPublishPrefix(),
            entryPath,
            Instant.now().toEpochMilli(),
            expiresAt
        );

        try {
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set("ticket:content:" + digest, json, TICKET_TTL);
            redisTemplate.opsForSet().add("user:tickets:" + user.id(), digest);
            redisTemplate.opsForSet().add("version:tickets:" + version.getId(), digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store ticket in Redis: " + e.getMessage(), e);
        }

        String contentUrl = previewBaseUrl + "/content/c/" + rawTicket + "/" + entryPath;
        return new PreviewTicketResponse(rawTicket, contentUrl, expiresAt);
    }

    public void revokeVersionTickets(Long versionId) {
        try {
            Set<String> digests = redisTemplate.opsForSet().members("version:tickets:" + versionId);
            if (digests != null && !digests.isEmpty()) {
                for (String digest : digests) {
                    redisTemplate.delete("ticket:content:" + digest);
                }
                redisTemplate.delete("version:tickets:" + versionId);
            }
        } catch (Exception e) {
            log.warn("Failed to revoke tickets for version {}: {}", versionId, e.getMessage());
        }
    }

    /** 撤销某用户签发的全部内部内容票据（禁用账号时调用）。 */
    public void revokeUserTickets(Long userId) {
        try {
            Set<String> digests = redisTemplate.opsForSet().members("user:tickets:" + userId);
            if (digests != null && !digests.isEmpty()) {
                for (String digest : digests) {
                    redisTemplate.delete("ticket:content:" + digest);
                }
                redisTemplate.delete("user:tickets:" + userId);
            }
        } catch (Exception e) {
            log.warn("Failed to revoke tickets for user {}: {}", userId, e.getMessage());
        }
    }

    public static String hashTicket(String rawTicket) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawTicket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable: " + e.getMessage(), e);
        }
    }
}
