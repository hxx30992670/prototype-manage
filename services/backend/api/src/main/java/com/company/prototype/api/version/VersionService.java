package com.company.prototype.api.version;

import com.company.prototype.api.audit.Audited;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.PublishJobEntity;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.PublishJobStatus;
import com.company.prototype.persistence.publish.PublishStage;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PublishJobRepository publishJobRepository;
    private final TemporaryUploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrototypeAuthorizationService authorizationService;
    private final ObjectStorage objectStorage;
    private final StringRedisTemplate redisTemplate;
    private final PublicIdGenerator idGenerator;

    public VersionService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PublishJobRepository publishJobRepository,
        TemporaryUploadRepository uploadRepository,
        UserRepository userRepository,
        AuditLogRepository auditLogRepository,
        PrototypeAuthorizationService authorizationService,
        ObjectStorage objectStorage,
        StringRedisTemplate redisTemplate,
        PublicIdGenerator idGenerator
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.publishJobRepository = publishJobRepository;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
        this.objectStorage = objectStorage;
        this.redisTemplate = redisTemplate;
        this.idGenerator = idGenerator;
    }

    @Audited(action = "VERSION_CREATE", targetType = "VERSION")
    @Transactional
    public VersionDtos.CreateVersionResponse createVersion(CurrentUser user, String prototypePublicId, VersionDtos.CreateVersionRequest req) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeEntity lockedProto = prototypeRepository.findByIdForUpdate(proto.getId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (lockedProto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED);
        }

        boolean hasActive = publishJobRepository.existsByPrototypeIdAndStatusIn(
            lockedProto.getId(), Set.of(PublishJobStatus.PENDING, PublishJobStatus.RUNNING)
        );
        if (hasActive) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_PUBLISH_IN_PROGRESS);
        }

        TemporaryUploadEntity upload = uploadRepository.findByPublicId(req.uploadId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "上传会话不存在"));

        if (!upload.getUser().getId().equals(user.id()) && !user.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        if (!"COMPLETED".equalsIgnoreCase(upload.getStatus())) {
            throw new ApiException(ApiErrorCode.UPLOAD_OBJECT_MISSING, "上传文件未复核完成");
        }

        if (!req.sourceType().equalsIgnoreCase(upload.getFileType())) {
            throw new ApiException(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED, "版本来源类型与上传文件类型不一致");
        }

        int nextVersionNo = versionRepository.findMaxVersionNoByPrototypeId(lockedProto.getId()) + 1;
        String ext = req.sourceType().equalsIgnoreCase("ZIP") ? "zip" : "html";
        String sourceKey = "prototypes/" + lockedProto.getId() + "/versions/" + nextVersionNo + "/source." + ext;

        try (InputStream in = objectStorage.open(upload.getObjectKey())) {
            objectStorage.put(sourceKey, in, upload.getClaimedSize(), upload.getFileType().equalsIgnoreCase("ZIP") ? "application/zip" : "text/html");
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy temporary upload to permanent storage: " + e.getMessage(), e);
        }

        UserEntity userEntity = userRepository.findById(user.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        PrototypeVersionEntity version = new PrototypeVersionEntity();
        version.setPublicId(idGenerator.nextId());
        version.setPrototype(lockedProto);
        version.setVersionNo(nextVersionNo);
        version.setChangeLog(req.changeLog());
        version.setSourceType(req.sourceType().toUpperCase());
        version.setSourceObjectKey(sourceKey);
        version.setSourceSize(upload.getClaimedSize());
        version.setChecksum(upload.getChecksum() != null ? upload.getChecksum() : "none");
        version.setStatus(VersionStatus.PUBLISHING);
        version.setCreatedBy(userEntity);
        version = versionRepository.save(version);

        PublishJobEntity job = new PublishJobEntity();
        job.setPrototype(lockedProto);
        job.setVersion(version);
        job.setStatus(PublishJobStatus.PENDING);
        job.setStage(PublishStage.UPLOAD_CHECK);
        job.setProgress(0);
        job = publishJobRepository.save(job);

        redisTemplate.delete("prototype:current-version:" + lockedProto.getPublicId());

        return new VersionDtos.CreateVersionResponse(
            version.getPublicId(),
            version.getVersionNo(),
            version.getStatus().name(),
            job.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<VersionDtos.VersionItemResponse> listVersions(CurrentUser user, String prototypePublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        List<PrototypeVersionEntity> versions = versionRepository.findAllByPrototypeIdOrderByVersionNoDesc(proto.getId());
        return versions.stream().map(v -> toItemResponse(v, proto)).toList();
    }

    @Transactional(readOnly = true)
    public VersionDtos.VersionItemResponse getVersion(CurrentUser user, String prototypePublicId, String versionPublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        return toItemResponse(version, proto);
    }

    public VersionDtos.PublishJobStatusResponse getPublishJobStatus(CurrentUser user, String prototypePublicId, String versionPublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canView(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        PublishJobEntity job = publishJobRepository.findByVersionId(version.getId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "发布任务不存在"));

        return new VersionDtos.PublishJobStatusResponse(
            job.getId(),
            job.getStatus().name(),
            job.getStage() != null ? job.getStage().name() : null,
            job.getProgress(),
            job.getErrorDetail(),
            job.getCreatedAt(),
            job.getFinishedAt()
        );
    }

    @Transactional
    public VersionDtos.VersionItemResponse switchCurrent(
        CurrentUser user,
        String prototypePublicId,
        String versionPublicId,
        VersionDtos.SwitchCurrentVersionRequest req
    ) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeEntity lockedProto = prototypeRepository.findByIdForUpdate(proto.getId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (lockedProto.isArchived()) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_ARCHIVED);
        }

        boolean hasActive = publishJobRepository.existsByPrototypeIdAndStatusIn(
            lockedProto.getId(), Set.of(PublishJobStatus.PENDING, PublishJobStatus.RUNNING)
        );
        if (hasActive) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_PUBLISH_IN_PROGRESS);
        }

        PrototypeVersionEntity targetVersion = versionRepository.findByPrototypeIdAndPublicId(lockedProto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (targetVersion.getStatus() != VersionStatus.PUBLISHED) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "只能切换至已发布的版本");
        }

        if (req.expectedCurrentVersionNo() != null) {
            Integer currentVersionNo = null;
            if (lockedProto.getCurrentVersionId() != null) {
                PrototypeVersionEntity current = versionRepository.findById(lockedProto.getCurrentVersionId()).orElse(null);
                if (current != null) {
                    currentVersionNo = current.getVersionNo();
                }
            }
            if (!req.expectedCurrentVersionNo().equals(currentVersionNo)) {
                throw new ApiException(ApiErrorCode.RESOURCE_VERSION_CONFLICT);
            }
        }

        lockedProto.setCurrentVersionId(targetVersion.getId());
        prototypeRepository.save(lockedProto);

        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(UUID.randomUUID().toString());
        audit.setAction("SWITCH_VERSION");
        audit.setTargetType("PROTOTYPE");
        audit.setTargetId(lockedProto.getPublicId());
        audit.setActorType("USER");
        audit.setActorId(user.id());
        audit.setResult("SUCCESS");
        audit.setSummary("切换当前版本至 v" + targetVersion.getVersionNo() + ", 原因: " + req.reason());
        audit.setCreatedAt(Instant.now());
        auditLogRepository.save(audit);

        redisTemplate.delete("prototype:current-version:" + lockedProto.getPublicId());

        return toItemResponse(targetVersion, lockedProto);
    }

    public VersionDtos.DownloadTicketResponse createDownloadTicket(CurrentUser user, String prototypePublicId, String versionPublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        String ticket = UUID.randomUUID().toString().replace("-", "");
        String ext = version.getSourceType().equalsIgnoreCase("ZIP") ? ".zip" : ".html";
        String filename = proto.getCode() + "-v" + version.getVersionNo() + ext;
        String val = version.getSourceObjectKey() + "|" + filename;

        redisTemplate.opsForValue().set("download:ticket:" + ticket, val, Duration.ofMinutes(5));

        return new VersionDtos.DownloadTicketResponse("/api/v1/downloads/" + ticket, Instant.now().plus(Duration.ofMinutes(5)));
    }

    @Audited(action = "VERSION_DELETE", targetType = "VERSION")
    @Transactional
    public void deleteVersion(CurrentUser user, String prototypePublicId, String versionPublicId) {
        PrototypeEntity proto = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototypePublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!authorizationService.canManage(user, proto)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }

        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(proto.getId(), versionPublicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (version.getId().equals(proto.getCurrentVersionId())) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "当前生效版本不可删除");
        }

        if (version.getStatus() == VersionStatus.PUBLISHING) {
            throw new ApiException(ApiErrorCode.PROTOTYPE_PUBLISH_IN_PROGRESS);
        }

        publishJobRepository.findByVersionId(version.getId()).ifPresent(publishJobRepository::delete);
        versionRepository.delete(version);

        try {
            objectStorage.deletePrefix("prototypes/" + proto.getId() + "/versions/" + version.getId() + "/");
            objectStorage.deletePrefix("prototypes/" + proto.getId() + "/versions/" + version.getVersionNo() + "/");
        } catch (Exception e) {
            log.warn("Failed to delete storage files for version {}: {}", version.getId(), e.getMessage());
        }
    }

    private VersionDtos.VersionItemResponse toItemResponse(PrototypeVersionEntity v, PrototypeEntity proto) {
        boolean isCurrent = v.getId().equals(proto.getCurrentVersionId());
        String createdBy = v.getCreatedBy() != null ? v.getCreatedBy().getDisplayName() : null;
        return new VersionDtos.VersionItemResponse(
            v.getPublicId(),
            v.getVersionNo(),
            v.getChangeLog(),
            v.getChangeLog(),
            v.getStatus().name(),
            v.getSourceType(),
            v.getSourceSize() != null ? v.getSourceSize() : 0L,
            v.getEntryPath(),
            v.getFileCount() != null ? v.getFileCount() : 0,
            v.getExpandedSize() != null ? v.getExpandedSize() : 0L,
            isCurrent,
            v.getFailureStage(),
            v.getFailureMessage(),
            createdBy,
            v.getCreatedAt(),
            v.getPublishedAt()
        );
    }
}
