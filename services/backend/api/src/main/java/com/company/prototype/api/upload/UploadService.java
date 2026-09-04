package com.company.prototype.api.upload;

import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class UploadService {

    /** 封面大小固定为 5MB，不随系统配置调整（设计文档 §10.3）。 */
    public static final long MAX_COVER_BYTES = 5 * 1024 * 1024L;

    private static final Set<String> ALLOWED_TYPES = Set.of("HTML", "ZIP", "COVER", "ATTACHMENT");
    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ObjectStorage objectStorage;
    private final TemporaryUploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final PublicIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final AdminConfigService configService;
    private Clock clock = Clock.systemUTC();

    public UploadService(
        ObjectStorage objectStorage,
        TemporaryUploadRepository uploadRepository,
        UserRepository userRepository,
        PublicIdGenerator idGenerator,
        StringRedisTemplate redisTemplate,
        @Autowired(required = false) AdminConfigService configService
    ) {
        this.objectStorage = objectStorage;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public UploadDtos.CreateUploadResponse create(CurrentUser user, UploadDtos.CreateUploadRequest req, String idempotencyKey) {
        String fileType = req.fileType().toUpperCase();
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new ApiException(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED);
        }

        validateSize(fileType, req.claimedSize());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = "idempotency:upload:" + user.id() + ":" + idempotencyKey;
            String existingUploadId = redisTemplate.opsForValue().get(redisKey);
            if (existingUploadId != null) {
                TemporaryUploadEntity existing = uploadRepository.findByPublicId(existingUploadId).orElse(null);
                if (existing != null) {
                    if (!existing.getFilename().equals(req.filename())
                        || !existing.getFileType().equals(fileType)
                        || !existing.getClaimedSize().equals(req.claimedSize())) {
                        throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
                    }
                    // Refresh expired URL
                    ObjectStorage.PresignedUpload refreshed = objectStorage.createUpload(
                        existing.getObjectKey(), existing.getClaimedSize(), UPLOAD_TTL
                    );
                    existing.setExpiresAt(Instant.now(clock).plus(UPLOAD_TTL));
                    uploadRepository.save(existing);
                    return new UploadDtos.CreateUploadResponse(
                        existing.getPublicId(),
                        refreshed.uploadUrl(),
                        existing.getExpiresAt(),
                        Map.of()
                    );
                }
            }
        }

        UserEntity userEntity = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        String uploadId = idGenerator.nextId();
        String objectKey = "temporary/" + uploadId + "/source";
        if ("COVER".equals(fileType) || "ATTACHMENT".equals(fileType)) {
            objectKey = "temporary/" + uploadId + "/" + fileType.toLowerCase();
        }

        ObjectStorage.PresignedUpload presigned = objectStorage.createUpload(objectKey, req.claimedSize(), UPLOAD_TTL);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(uploadId);
        entity.setUser(userEntity);
        entity.setFilename(req.filename());
        entity.setFileType(fileType);
        entity.setObjectKey(objectKey);
        entity.setClaimedSize(req.claimedSize());
        entity.setStatus("PENDING");
        entity.setExpiresAt(Instant.now(clock).plus(UPLOAD_TTL));
        entity.setCreatedAt(Instant.now(clock));
        uploadRepository.save(entity);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = "idempotency:upload:" + user.id() + ":" + idempotencyKey;
            redisTemplate.opsForValue().set(redisKey, uploadId, IDEMPOTENCY_TTL);
        }

        return new UploadDtos.CreateUploadResponse(
            uploadId,
            presigned.uploadUrl(),
            entity.getExpiresAt(),
            Map.of()
        );
    }

    @Transactional
    public UploadDtos.UploadInfoResponse complete(CurrentUser user, String uploadId, String checksum) {
        TemporaryUploadEntity upload = uploadRepository.findByPublicId(uploadId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!upload.getUser().getId().equals(user.id()) && !user.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (upload.getExpiresAt().isBefore(Instant.now(clock))) {
            throw new ApiException(ApiErrorCode.UPLOAD_EXPIRED);
        }

        ObjectStorage.ObjectMetadata meta = objectStorage.stat(upload.getObjectKey());
        if (meta == null) {
            throw new ApiException(ApiErrorCode.UPLOAD_OBJECT_MISSING);
        }

        if (meta.size() != upload.getClaimedSize()) {
            throw new ApiException(ApiErrorCode.UPLOAD_SIZE_MISMATCH);
        }

        if (checksum != null && !checksum.isBlank() && meta.checksum() != null) {
            String cleanChecksum = checksum.trim().toLowerCase();
            String metaChecksum = meta.checksum().toLowerCase();
            if (!metaChecksum.equalsIgnoreCase(cleanChecksum)) {
                throw new ApiException(ApiErrorCode.UPLOAD_CHECKSUM_MISMATCH);
            }
        }

        upload.setActualSize(meta.size());
        upload.setChecksum(checksum != null && !checksum.isBlank() ? checksum.trim() : meta.checksum());
        upload.setStatus("COMPLETED");
        upload = uploadRepository.save(upload);

        return toInfoResponse(upload);
    }

    public UploadDtos.UploadInfoResponse getUploadInfo(CurrentUser user, String uploadId) {
        TemporaryUploadEntity upload = uploadRepository.findByPublicId(uploadId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!upload.getUser().getId().equals(user.id()) && !user.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        return toInfoResponse(upload);
    }

    private void validateSize(String fileType, long size) {
        switch (fileType) {
            case "HTML" -> {
                if (size > mb(config("upload.html.maxMb", 20))) {
                    throw new ApiException(ApiErrorCode.UPLOAD_HTML_TOO_LARGE);
                }
            }
            case "ZIP" -> {
                if (size > mb(config("upload.zip.maxMb", 200))) {
                    throw new ApiException(ApiErrorCode.UPLOAD_ZIP_TOO_LARGE);
                }
            }
            case "COVER" -> {
                if (size > MAX_COVER_BYTES) {
                    throw new ApiException(ApiErrorCode.ATTACHMENT_SIZE_LIMIT);
                }
            }
            case "ATTACHMENT" -> {
                if (size > mb(config("attachment.maxMb", 50))) {
                    throw new ApiException(ApiErrorCode.ATTACHMENT_SIZE_LIMIT);
                }
            }
        }
    }

    private long mb(long mbValue) {
        return mbValue * 1024 * 1024L;
    }

    private long config(String key, long defaultValue) {
        if (configService == null) {
            return defaultValue;
        }
        try {
            return configService.getLong(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private UploadDtos.UploadInfoResponse toInfoResponse(TemporaryUploadEntity upload) {
        return new UploadDtos.UploadInfoResponse(
            upload.getPublicId(),
            upload.getFilename(),
            upload.getFileType(),
            upload.getClaimedSize(),
            upload.getActualSize(),
            upload.getChecksum(),
            upload.getStatus(),
            upload.getExpiresAt(),
            upload.getCreatedAt()
        );
    }
}
