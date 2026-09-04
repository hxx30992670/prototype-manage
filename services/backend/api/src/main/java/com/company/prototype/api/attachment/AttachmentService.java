package com.company.prototype.api.attachment;

import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.api.audit.Audited;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.attachment.PrototypeAttachmentEntity;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Set<String> ALLOWED_COVER_EXTS = Set.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_COVER_SIZE = 5 * 1024 * 1024; // 5MB

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeAttachmentRepository attachmentRepository;
    private final TemporaryUploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final ObjectStorage objectStorage;
    private final StringRedisTemplate redisTemplate;
    private final PublicIdGenerator idGenerator;
    private final AdminConfigService configService;

    public AttachmentService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeAttachmentRepository attachmentRepository,
        TemporaryUploadRepository uploadRepository,
        UserRepository userRepository,
        PrototypeAuthorizationService authorizationService,
        ObjectStorage objectStorage,
        StringRedisTemplate redisTemplate,
        PublicIdGenerator idGenerator,
        @org.springframework.beans.factory.annotation.Autowired(required = false) AdminConfigService configService
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.attachmentRepository = attachmentRepository;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.objectStorage = objectStorage;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
        this.configService = configService;
    }

    @Audited(action = "ATTACHMENT_CREATE", targetType = "ATTACHMENT")
    @Transactional
    public AttachmentDtos.AttachmentItemResponse createAttachment(
        CurrentUser user,
        String prototypePublicId,
        AttachmentDtos.CreateAttachmentRequest req
    ) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可添加附件");
        }

        TemporaryUploadEntity upload = uploadRepository.findByPublicId(req.uploadId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "上传会话不存在"));

        assertUploadOwnedBy(user, upload);
        if (!"ATTACHMENT".equalsIgnoreCase(upload.getFileType())) {
            throw new ApiException(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED, "上传类型必须为附件");
        }

        if (!"COMPLETED".equalsIgnoreCase(upload.getStatus())) {
            throw new ApiException(ApiErrorCode.UPLOAD_OBJECT_MISSING, "附件上传未完成复核");
        }

        long used = attachmentRepository.sumSizeByPrototypeId(proto.getId());
        long maxBytes = attachmentTotalLimitBytes();
        if (used + upload.getClaimedSize() > maxBytes) {
            throw new ApiException(ApiErrorCode.ATTACHMENT_SIZE_LIMIT, "单原型附件总容量已超出限制");
        }

        PrototypeVersionEntity version = null;
        if (req.versionPublicId() != null && !req.versionPublicId().isBlank()) {
            version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), req.versionPublicId())
                .orElse(null);
        }

        UserEntity userEntity = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        String attachmentPublicId = idGenerator.nextId();
        String targetKey = "prototypes/" + proto.getId() + "/attachments/" + attachmentPublicId + "/original";
        String mimeType = inferMimeType(upload.getFilename());

        try (InputStream in = objectStorage.open(upload.getObjectKey())) {
            objectStorage.put(targetKey, in, upload.getClaimedSize(), mimeType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy attachment to permanent storage: " + e.getMessage(), e);
        }

        PrototypeAttachmentEntity entity = new PrototypeAttachmentEntity();
        entity.setPublicId(attachmentPublicId);
        entity.setPrototype(proto);
        entity.setVersion(version);
        entity.setName(req.name());
        entity.setType(req.type().toUpperCase());
        entity.setPurpose(req.purpose());
        entity.setAccessScope(req.accessScope().toUpperCase());
        entity.setObjectKey(targetKey);
        entity.setSize(upload.getClaimedSize());
        entity.setMimeType(mimeType);
        entity.setChecksum(upload.getChecksum() != null ? upload.getChecksum() : "none");
        entity.setCreatedBy(userEntity);
        entity.setCreatedAt(Instant.now());

        entity = attachmentRepository.save(entity);
        return toItemResponse(entity);
    }

    private String inferMimeType(String filename) {
        String lower = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        int dot = lower.lastIndexOf('.');
        String extension = dot >= 0 ? lower.substring(dot + 1) : "";
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "txt", "log" -> "text/plain";
            case "csv" -> "text/csv";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    @Transactional(readOnly = true)
    public List<AttachmentDtos.AttachmentItemResponse> listAttachments(CurrentUser user, String prototypePublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        List<PrototypeAttachmentEntity> list = attachmentRepository.findAllByPrototypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(proto.getId());
        return list.stream().map(this::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AttachmentDtos.AttachmentItemResponse> listGlobalAttachments(
        CurrentUser user,
        String prototypePublicId,
        String type,
        String createdBy
    ) {
        List<PrototypeAttachmentEntity> all;
        if (prototypePublicId != null && !prototypePublicId.isBlank()) {
            return listAttachments(user, prototypePublicId);
        } else {
            all = attachmentRepository.findAll().stream()
                .filter(a -> a.getDeletedAt() == null)
                .filter(a -> authorizationService.canView(user, a.getPrototype()))
                .filter(a -> type == null || a.getType().equalsIgnoreCase(type))
                .filter(a -> createdBy == null || (a.getCreatedBy() != null && a.getCreatedBy().getUsername().equalsIgnoreCase(createdBy)))
                .toList();
        }
        return all.stream().map(this::toItemResponse).toList();
    }

    @Audited(action = "ATTACHMENT_DELETE", targetType = "ATTACHMENT")
    @Transactional
    public void deleteAttachment(CurrentUser user, String publicId) {
        PrototypeAttachmentEntity entity = attachmentRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, entity.getPrototype())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        entity.setDeletedAt(Instant.now());
        attachmentRepository.save(entity);
    }

    public AttachmentDtos.DownloadTicketResponse createDownloadTicket(CurrentUser user, String publicId) {
        PrototypeAttachmentEntity entity = attachmentRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, entity.getPrototype())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        String ticket = UUID.randomUUID().toString().replace("-", "");
        String val = entity.getObjectKey() + "|" + entity.getName();

        redisTemplate.opsForValue().set("download:ticket:" + ticket, val, Duration.ofMinutes(5));
        return new AttachmentDtos.DownloadTicketResponse("/api/v1/downloads/" + ticket, Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Audited(action = "PROTOTYPE_COVER_UPDATE", targetType = "PROTOTYPE")
    @Transactional
    public void uploadCover(CurrentUser user, String prototypePublicId, AttachmentDtos.UploadCoverRequest req) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (proto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED, "原型已归档，不可更新封面");
        }

        TemporaryUploadEntity upload = uploadRepository.findByPublicId(req.uploadId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "上传会话不存在"));

        assertUploadOwnedBy(user, upload);
        if (!"COVER".equalsIgnoreCase(upload.getFileType())) {
            throw new ApiException(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED, "上传类型必须为封面");
        }

        if (!"COMPLETED".equalsIgnoreCase(upload.getStatus())) {
            throw new ApiException(ApiErrorCode.UPLOAD_OBJECT_MISSING, "封面上传未完成复核");
        }

        if (upload.getClaimedSize() > MAX_COVER_SIZE) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "封面图片不能超过 5MB");
        }

        String filename = upload.getFilename() != null ? upload.getFilename().toLowerCase(Locale.ROOT) : "";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        if (!ALLOWED_COVER_EXTS.contains(ext)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "封面格式仅支持 PNG、JPEG 或 WebP");
        }

        String coverKey = "covers/" + proto.getId() + "/original";
        try (InputStream in = objectStorage.open(upload.getObjectKey())) {
            objectStorage.put(coverKey, in, upload.getClaimedSize(), "image/" + (ext.equals("jpg") ? "jpeg" : ext));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save cover: " + e.getMessage(), e);
        }

        proto.setCoverObjectKey(coverKey);
        prototypeRepository.save(proto);
    }

    private void assertUploadOwnedBy(CurrentUser user, TemporaryUploadEntity upload) {
        if (!upload.getUser().getId().equals(user.id()) && !user.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "不能使用他人的上传会话");
        }
    }

    private long attachmentTotalLimitBytes() {
        long gb = 1L;
        if (configService != null) {
            try {
                gb = configService.getLong("attachment.totalPerPrototypeGb");
            } catch (Exception ignored) {
                gb = 1L;
            }
        }
        return gb * 1024L * 1024L * 1024L;
    }

    private AttachmentDtos.AttachmentItemResponse toItemResponse(PrototypeAttachmentEntity entity) {
        String createdBy = entity.getCreatedBy() != null ? entity.getCreatedBy().getDisplayName() : null;
        String verPublicId = entity.getVersion() != null ? entity.getVersion().getPublicId() : null;
        Integer verNo = entity.getVersion() != null ? entity.getVersion().getVersionNo() : null;
        return new AttachmentDtos.AttachmentItemResponse(
            entity.getPublicId(),
            entity.getPrototype().getPublicId(),
            verPublicId,
            verNo,
            entity.getName(),
            entity.getType(),
            entity.getPurpose(),
            entity.getAccessScope(),
            entity.getSize(),
            entity.getMimeType(),
            createdBy,
            entity.getCreatedAt()
        );
    }
}
