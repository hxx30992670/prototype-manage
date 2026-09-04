package com.company.prototype.api.share;

import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.api.audit.Audited;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.ticket.ContentTicketRecord;
import com.company.prototype.persistence.attachment.PrototypeAttachmentEntity;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.share.PrototypeShareLinkEntity;
import com.company.prototype.persistence.share.PrototypeShareLinkRepository;
import com.company.prototype.persistence.spec.PrototypeSpecEntity;
import com.company.prototype.persistence.spec.PrototypeSpecRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PrototypeShareService {

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeSpecRepository specRepository;
    private final PrototypeAttachmentRepository attachmentRepository;
    private final PrototypeShareLinkRepository shareLinkRepository;
    private final UserRepository userRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final String previewBaseUrl;
    private final AdminConfigService configService;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.company.prototype.api.monitoring.PlatformMetrics platformMetrics;

    public PrototypeShareService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeSpecRepository specRepository,
        PrototypeAttachmentRepository attachmentRepository,
        PrototypeShareLinkRepository shareLinkRepository,
        UserRepository userRepository,
        PrototypeAuthorizationService authorizationService,
        PasswordEncoder passwordEncoder,
        StringRedisTemplate redisTemplate,
        PublicIdGenerator idGenerator,
        @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
        @Value("${preview.base-url:http://preview.corp.test}") String previewBaseUrl,
        @org.springframework.beans.factory.annotation.Autowired(required = false) AdminConfigService configService
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.specRepository = specRepository;
        this.attachmentRepository = attachmentRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            this.objectMapper = mapper;
        }
        this.previewBaseUrl = previewBaseUrl.endsWith("/") ? previewBaseUrl.substring(0, previewBaseUrl.length() - 1) : previewBaseUrl;
        this.configService = configService;
    }

    @Audited(action = "SHARE_CREATE", targetType = "SHARE")
    @Transactional
    public ShareDtos.ShareCreatedResponse createShareLink(
        CurrentUser user,
        String prototypePublicId,
        ShareDtos.CreateShareRequest req,
        String idempotencyKey
    ) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可创建分享链接");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String existingShareId = redisTemplate.opsForValue().get("share:idempotency:" + idempotencyKey);
            if (existingShareId != null) {
                return new ShareDtos.ShareCreatedResponse(existingShareId, null, false);
            }
        }

        UserEntity creator = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        String rawToken = generateRawToken();
        String tokenDigest = sha256(rawToken);
        String sharePublicId = idGenerator.nextId();

        PrototypeShareLinkEntity link = new PrototypeShareLinkEntity();
        link.setPublicId(sharePublicId);
        link.setPrototype(proto);
        link.setName(req.name());
        link.setTokenDigest(tokenDigest);
        if (req.password() != null && !req.password().isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        link.setExpiresAt(req.expiresAt());
        link.setStatus("ACTIVE");
        link.setSpecScope(req.specScope() != null ? req.specScope().toUpperCase() : "NONE");
        link.setAllowComment(Boolean.TRUE.equals(req.allowComment()));
        link.setAllowPublicAttachment(Boolean.TRUE.equals(req.allowPublicAttachment()));
        link.setAuthEpoch(0L);
        link.setVisitCount(0L);
        link.setCreatedBy(creator);
        link.setCreatedAt(Instant.now());

        link = shareLinkRepository.save(link);
        cacheShareAuthEpoch(link);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set("share:idempotency:" + idempotencyKey, sharePublicId, Duration.ofHours(24));
        }

        String rawUrl = previewBaseUrl + "/s/" + rawToken;
        return new ShareDtos.ShareCreatedResponse(sharePublicId, rawUrl, true);
    }

    @Transactional(readOnly = true)
    public List<ShareDtos.ShareLinkItemResponse> listShareLinks(CurrentUser user, String prototypePublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        List<PrototypeShareLinkEntity> list = shareLinkRepository.findAllByPrototypeIdOrderByCreatedAtDesc(proto.getId());
        return list.stream().map(this::toItemResponse).toList();
    }

    @Audited(action = "SHARE_ROTATE_TOKEN", targetType = "SHARE")
    @Transactional
    public ShareDtos.RotateTokenResponse rotateToken(CurrentUser user, String sharePublicId) {
        PrototypeShareLinkEntity link = shareLinkRepository.findByPublicId(sharePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, link.getPrototype())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        String rawToken = generateRawToken();
        link.setTokenDigest(sha256(rawToken));
        link.setAuthEpoch(link.getAuthEpoch() + 1);
        shareLinkRepository.save(link);
        cacheShareAuthEpoch(link);

        invalidateShareSessions(link.getId());

        String rawUrl = previewBaseUrl + "/s/" + rawToken;
        return new ShareDtos.RotateTokenResponse(rawUrl, true);
    }

    @Audited(action = "SHARE_RESET_PASSWORD", targetType = "SHARE")
    @Transactional
    public void resetPassword(CurrentUser user, String sharePublicId, String newPassword) {
        PrototypeShareLinkEntity link = shareLinkRepository.findByPublicId(sharePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, link.getPrototype())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(newPassword));
        } else {
            link.setPasswordHash(null);
        }
        link.setAuthEpoch(link.getAuthEpoch() + 1);
        shareLinkRepository.save(link);
        cacheShareAuthEpoch(link);

        invalidateShareSessions(link.getId());
    }

    @Audited(action = "SHARE_UPDATE_STATUS", targetType = "SHARE")
    @Transactional
    public void updateStatus(CurrentUser user, String sharePublicId, String status) {
        PrototypeShareLinkEntity link = shareLinkRepository.findByPublicId(sharePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, link.getPrototype())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        link.setStatus(status.toUpperCase());
        link.setAuthEpoch(link.getAuthEpoch() + 1);
        shareLinkRepository.save(link);
        cacheShareAuthEpoch(link);

        invalidateShareSessions(link.getId());
    }

    // Public / Share-facing operations

    public record BootstrapResult(ShareDtos.BootstrapResponse response, String sessionId, Duration ttl) {}

    @Transactional
    public BootstrapResult bootstrapShare(String rawToken, String sessionCookie) {
        String tokenDigest = sha256(rawToken);
        PrototypeShareLinkEntity link = shareLinkRepository.findByTokenDigest(tokenDigest)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "分享链接不存在"));

        PrototypeEntity proto = link.getPrototype();
        if (proto.isArchived() || proto.getDeletedAt() != null || proto.getCurrentVersionId() == null) {
            return new BootstrapResult(buildBootstrap(
                proto, false, false, null, "NONE", null, List.of(), false, "原型不可用"
            ), null, null);
        }

        if (!isShareActive(link)) {
            return new BootstrapResult(buildBootstrap(
                proto, false, false, null, "NONE", null, List.of(), false, "分享链接已失效"
            ), null, null);
        }

        link.setVisitCount(link.getVisitCount() + 1);
        link.setLastVisitedAt(Instant.now());
        shareLinkRepository.save(link);

        boolean hasPassword = hasPassword(link);
        ShareDtos.ShareSessionRecord session = getValidSession(sessionCookie, link);

        if (hasPassword && session == null) {
            return new BootstrapResult(buildBootstrap(
                proto, true, false, null, "NONE", null, List.of(), false, "OK"
            ), null, null);
        }

        String csrfToken = UUID.randomUUID().toString();
        Duration ttl = calculateSessionTtl(link.getExpiresAt());
        String sessionId;
        if (session != null && sessionCookie != null && !sessionCookie.isBlank()) {
            sessionId = sessionCookie;
            saveSessionWithId(sessionId, link, csrfToken, session.guestName(), ttl);
        } else {
            sessionId = UUID.randomUUID().toString();
            saveSessionWithId(sessionId, link, csrfToken, "访客", ttl);
        }

        Map<String, Object> specData = null;
        if (!"NONE".equalsIgnoreCase(link.getSpecScope())) {
            specData = getFilteredSpec(proto.getId(), link.getSpecScope());
        }

        List<Map<String, Object>> attachments = List.of();
        if (Boolean.TRUE.equals(link.getAllowPublicAttachment())) {
            attachments = attachmentRepository.findAllByPrototypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(proto.getId()).stream()
                .filter(a -> "PUBLIC".equalsIgnoreCase(a.getAccessScope()))
                .map(a -> Map.<String, Object>of(
                    "publicId", a.getPublicId(),
                    "name", a.getName(),
                    "type", a.getType(),
                    "size", a.getSize(),
                    "mimeType", a.getMimeType()
                ))
                .toList();
        }

        return new BootstrapResult(buildBootstrap(
            proto,
            false,
            true,
            csrfToken,
            link.getSpecScope(),
            specData,
            attachments,
            Boolean.TRUE.equals(link.getAllowComment()),
            "OK"
        ), sessionId, ttl);
    }

    private ShareDtos.BootstrapResponse buildBootstrap(
        PrototypeEntity proto,
        boolean requiresPassword,
        boolean authenticated,
        String csrfToken,
        String specScope,
        Map<String, Object> spec,
        List<Map<String, Object>> attachments,
        boolean allowComment,
        String statusMessage
    ) {
        String currentVersionPublicId = null;
        Integer currentVersionNo = null;
        if (proto.getCurrentVersionId() != null) {
            PrototypeVersionEntity current = versionRepository.findById(proto.getCurrentVersionId()).orElse(null);
            if (current != null) {
                currentVersionPublicId = current.getPublicId();
                currentVersionNo = current.getVersionNo();
            }
        }
        return new ShareDtos.BootstrapResponse(
            proto.getName(),
            proto.getPublicId(),
            requiresPassword,
            authenticated,
            csrfToken,
            specScope,
            spec,
            attachments,
            allowComment,
            statusMessage,
            currentVersionPublicId,
            currentVersionNo
        );
    }

    public record VerifyPasswordResult(ShareDtos.VerifyPasswordResponse response, String sessionId, Duration ttl) {}

    @Transactional(readOnly = true)
    public VerifyPasswordResult verifyPassword(String rawToken, ShareDtos.VerifyPasswordRequest req, String clientIp) {
        String tokenDigest = sha256(rawToken);
        PrototypeShareLinkEntity link = shareLinkRepository.findByTokenDigest(tokenDigest)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "分享链接不存在"));

        if (!"ACTIVE".equalsIgnoreCase(link.getStatus()) || (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now()))) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "分享链接已失效");
        }

        if (isPasswordRateLimited(link.getId(), clientIp)) {
            if (platformMetrics != null) {
                platformMetrics.sharePasswordFailure();
            }
            throw new ApiException(ApiErrorCode.SHARE_PASSWORD_RATE_LIMITED);
        }

        if (link.getPasswordHash() != null && !passwordEncoder.matches(req.password(), link.getPasswordHash())) {
            recordPasswordFailure(link.getId(), clientIp);
            if (platformMetrics != null) {
                platformMetrics.sharePasswordFailure();
            }
            throw new ApiException(ApiErrorCode.SHARE_PASSWORD_INCORRECT, "密码错误");
        }
        clearPasswordFailures(link.getId(), clientIp);

        String csrfToken = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        Duration ttl = calculateSessionTtl(link.getExpiresAt());

        saveSessionWithId(sessionId, link, csrfToken, req.guestName(), ttl);

        return new VerifyPasswordResult(
            new ShareDtos.VerifyPasswordResponse(true, csrfToken),
            sessionId,
            ttl
        );
    }

    private int passwordFailureLimit() {
        if (configService == null) {
            return 5;
        }
        try {
            return (int) Math.min(configService.getLong("share.passwordFailureLimit"), Integer.MAX_VALUE);
        } catch (Exception e) {
            return 5;
        }
    }

    private Duration passwordLockDuration() {
        if (configService == null) {
            return Duration.ofMinutes(15);
        }
        try {
            return Duration.ofMinutes(configService.getLong("share.passwordLockMinutes"));
        } catch (Exception e) {
            return Duration.ofMinutes(15);
        }
    }

    private boolean isPasswordRateLimited(long shareLinkId, String clientIp) {
        String key = "share:passfail:" + shareLinkId + ":" + clientIp;
        try {
            String val = redisTemplate.opsForValue().get(key);
            return val != null && Integer.parseInt(val) >= passwordFailureLimit();
        } catch (Exception e) {
            return false;
        }
    }

    private void recordPasswordFailure(long shareLinkId, String clientIp) {
        String key = "share:passfail:" + shareLinkId + ":" + clientIp;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, passwordLockDuration());
            }
        } catch (Exception e) {
            // Redis 不可用时退化为不锁定（不阻断访问）
        }
    }

    private void clearPasswordFailures(long shareLinkId, String clientIp) {
        try {
            redisTemplate.delete("share:passfail:" + shareLinkId + ":" + clientIp);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public ShareDtos.ContentTicketResponse issueContentTicket(String rawToken, String sessionCookie, String csrfToken) {
        PrototypeShareLinkEntity link = requireActiveShare(rawToken);
        PrototypeEntity proto = link.getPrototype();
        if (proto.isArchived() || proto.getDeletedAt() != null || proto.getCurrentVersionId() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "原型当前不可用");
        }

        ShareDtos.ShareSessionRecord session = requireSessionIfPasswordProtected(link, sessionCookie);

        PrototypeVersionEntity version = versionRepository.findById(proto.getCurrentVersionId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "当前版本不存在"));

        if (version.getStatus() != com.company.prototype.persistence.version.VersionStatus.PUBLISHED) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "当前版本尚未发布完成");
        }

        String rawTicket = idGenerator.nextId() + UUID.randomUUID().toString().replace("-", "");
        String ticketDigest = sha256(rawTicket);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(120));
        ContentTicketRecord record = new ContentTicketRecord(
            ticketDigest,
            "SHARE",
            session != null ? sessionCookie : "anonymous:" + link.getId(),
            version.getId(),
            version.getPublishPrefix(),
            version.getEntryPath(),
            link.getAuthEpoch(),
            expiresAt
        );

        try {
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set("ticket:content:" + ticketDigest, json, Duration.ofMinutes(120));
            redisTemplate.opsForSet().add("share:tickets:" + link.getId(), ticketDigest);
            redisTemplate.expire("share:tickets:" + link.getId(), Duration.ofMinutes(120));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize content ticket: " + e.getMessage(), e);
        }

        String contentUrl = previewBaseUrl + "/content/c/" + rawTicket + "/" + version.getEntryPath();
        return new ShareDtos.ContentTicketResponse(rawTicket, contentUrl, expiresAt);
    }

    public PrototypeShareLinkEntity requireActiveShare(String rawToken) {
        String tokenDigest = sha256(rawToken);
        PrototypeShareLinkEntity link = shareLinkRepository.findByTokenDigest(tokenDigest)
            .orElseThrow(() -> new ApiException(ApiErrorCode.SHARE_LINK_NOT_FOUND, "分享链接不存在"));
        if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
            throw new ApiException(ApiErrorCode.SHARE_LINK_DISABLED);
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ApiErrorCode.SHARE_LINK_EXPIRED);
        }
        return link;
    }

    public ShareDtos.ShareSessionRecord requireSessionIfPasswordProtected(PrototypeShareLinkEntity link, String sessionCookie) {
        ShareDtos.ShareSessionRecord session = getValidSession(sessionCookie, link);
        if (hasPassword(link) && session == null) {
            throw new ApiException(ApiErrorCode.SHARE_PASSWORD_REQUIRED, "未通过分享密码验证");
        }
        return session;
    }

    public ShareDtos.DownloadTicketResponse issuePublicAttachmentDownloadTicket(
        String rawToken,
        String attachmentPublicId,
        String sessionCookie
    ) {
        PrototypeShareLinkEntity link = requireActiveShare(rawToken);
        requireSessionIfPasswordProtected(link, sessionCookie);
        if (!Boolean.TRUE.equals(link.getAllowPublicAttachment())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "当前分享未开放公开附件");
        }
        PrototypeAttachmentEntity attachment = attachmentRepository.findByPublicIdAndDeletedAtIsNull(attachmentPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "附件不存在"));
        if (!attachment.getPrototype().getId().equals(link.getPrototype().getId())
            || !"PUBLIC".equalsIgnoreCase(attachment.getAccessScope())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "附件不可公开下载");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        String val = attachment.getObjectKey() + "|" + attachment.getName();
        redisTemplate.opsForValue().set("download:ticket:" + ticket, val, Duration.ofMinutes(5));
        return new ShareDtos.DownloadTicketResponse("/share-api/v1/downloads/" + ticket, Instant.now().plus(Duration.ofMinutes(5)));
    }

    private boolean isShareActive(PrototypeShareLinkEntity link) {
        return "ACTIVE".equalsIgnoreCase(link.getStatus())
            && (link.getExpiresAt() == null || !link.getExpiresAt().isBefore(Instant.now()));
    }

    private boolean hasPassword(PrototypeShareLinkEntity link) {
        return link.getPasswordHash() != null && !link.getPasswordHash().isBlank();
    }

    private ShareDtos.ShareSessionRecord getValidSession(String sessionId, PrototypeShareLinkEntity link) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String json = redisTemplate.opsForValue().get("share:session:" + sessionId);
        if (json == null) return null;

        try {
            ShareDtos.ShareSessionRecord session = objectMapper.readValue(json, ShareDtos.ShareSessionRecord.class);
            if (session.shareLinkId() == link.getId() && session.authEpoch() == link.getAuthEpoch()) {
                return session;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void saveSessionWithId(String sessionId, PrototypeShareLinkEntity link, String csrfToken, String guestName, Duration ttl) {
        ShareDtos.ShareSessionRecord session = new ShareDtos.ShareSessionRecord(
            link.getId(),
            link.getAuthEpoch(),
            sha256(csrfToken),
            guestName != null && !guestName.isBlank() ? guestName : "访客",
            Instant.now().plus(ttl)
        );

        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set("share:session:" + sessionId, json, ttl);
            redisTemplate.opsForSet().add("share:sessions:" + link.getId(), sessionId);
            redisTemplate.expire("share:sessions:" + link.getId(), ttl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save share session: " + e.getMessage(), e);
        }
    }

    private void invalidateShareSessions(long shareLinkId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members("share:sessions:" + shareLinkId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (String sid : sessionIds) {
                redisTemplate.delete("share:session:" + sid);
            }
            redisTemplate.delete("share:sessions:" + shareLinkId);
        }

        Set<String> ticketDigests = redisTemplate.opsForSet().members("share:tickets:" + shareLinkId);
        if (ticketDigests != null && !ticketDigests.isEmpty()) {
            for (String td : ticketDigests) {
                redisTemplate.delete("content:ticket:" + td);
                redisTemplate.delete("ticket:content:" + td);
            }
            redisTemplate.delete("share:tickets:" + shareLinkId);
        }
    }

    /**
     * 停用某原型的全部分享链接并递增 authEpoch、撤销会话与票据。
     * 用于原型删除（进回收站）与归档，保证分享立即失效。
     */
    @Transactional
    public int disableSharesForPrototype(Long prototypeId) {
        List<PrototypeShareLinkEntity> links = shareLinkRepository.findAllByPrototypeIdOrderByCreatedAtDesc(prototypeId);
        int disabled = 0;
        for (PrototypeShareLinkEntity link : links) {
            if (!"DISABLED".equals(link.getStatus())) {
                link.setStatus("DISABLED");
                link.setAuthEpoch(link.getAuthEpoch() + 1);
                shareLinkRepository.save(link);
                cacheShareAuthEpoch(link);
                disabled++;
            }
            invalidateShareSessions(link.getId());
        }
        return disabled;
    }

    private void cacheShareAuthEpoch(PrototypeShareLinkEntity link) {
        redisTemplate.opsForValue().set(
            "share:auth-epoch:" + link.getId(),
            String.valueOf(link.getAuthEpoch())
        );
    }

    private Duration calculateSessionTtl(Instant expiresAt) {
        Duration defaultTtl = Duration.ofHours(12);
        if (expiresAt != null) {
            Duration untilExpiry = Duration.between(Instant.now(), expiresAt);
            if (untilExpiry.isNegative()) return Duration.ofSeconds(1);
            if (untilExpiry.compareTo(defaultTtl) < 0) return untilExpiry;
        }
        return defaultTtl;
    }

    private Map<String, Object> getFilteredSpec(Long prototypeId, String specScope) {
        Optional<PrototypeSpecEntity> specOpt = specRepository.findByPrototypeId(prototypeId);
        if (specOpt.isEmpty()) return null;
        PrototypeSpecEntity s = specOpt.get();

        if ("CORE_FLOW".equalsIgnoreCase(specScope)) {
            return Map.of(
                "goal", s.getGoal() != null ? s.getGoal() : "",
                "coreFlow", s.getCoreFlow() != null ? s.getCoreFlow() : ""
            );
        }

        // ALL
        Map<String, Object> map = new HashMap<>();
        map.put("goal", s.getGoal() != null ? s.getGoal() : "");
        map.put("coreFlow", s.getCoreFlow() != null ? s.getCoreFlow() : "");
        map.put("interactionRules", s.getInteractionRules() != null ? s.getInteractionRules() : "");
        map.put("businessConstraints", s.getBusinessConstraints() != null ? s.getBusinessConstraints() : "");
        map.put("dataRequirements", s.getDataRequirements() != null ? s.getDataRequirements() : "");
        map.put("acceptanceNotes", s.getAcceptanceNotes() != null ? s.getAcceptanceNotes() : "");
        map.put("markdownExtra", s.getMarkdownExtra() != null ? s.getMarkdownExtra() : "");
        return map;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private ShareDtos.ShareLinkItemResponse toItemResponse(PrototypeShareLinkEntity link) {
        String createdBy = link.getCreatedBy() != null ? link.getCreatedBy().getDisplayName() : null;
        return new ShareDtos.ShareLinkItemResponse(
            link.getPublicId(),
            link.getName(),
            link.getExpiresAt(),
            link.getStatus(),
            link.getSpecScope(),
            Boolean.TRUE.equals(link.getAllowComment()),
            Boolean.TRUE.equals(link.getAllowPublicAttachment()),
            link.getPasswordHash() != null && !link.getPasswordHash().isBlank(),
            link.getAuthEpoch(),
            link.getVisitCount(),
            link.getLastVisitedAt(),
            createdBy,
            link.getCreatedAt()
        );
    }
}
