package com.company.prototype.api.version;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.PublishJobEntity;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.PublishJobStatus;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class VersionServiceTest {

    private PrototypeRepository prototypeRepository;
    private PrototypeVersionRepository versionRepository;
    private PublishJobRepository publishJobRepository;
    private TemporaryUploadRepository uploadRepository;
    private UserRepository userRepository;
    private AuditLogRepository auditLogRepository;
    private PrototypeAuthorizationService authorizationService;
    private ObjectStorage objectStorage;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    private VersionService service;

    private CurrentUser creatorUser;
    private UserEntity userEntity;
    private PrototypeEntity prototype;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        prototypeRepository = Mockito.mock(PrototypeRepository.class);
        versionRepository = Mockito.mock(PrototypeVersionRepository.class);
        publishJobRepository = Mockito.mock(PublishJobRepository.class);
        uploadRepository = Mockito.mock(TemporaryUploadRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        authorizationService = Mockito.mock(PrototypeAuthorizationService.class);
        objectStorage = Mockito.mock(ObjectStorage.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new VersionService(
            prototypeRepository,
            versionRepository,
            publishJobRepository,
            uploadRepository,
            userRepository,
            auditLogRepository,
            authorizationService,
            objectStorage,
            redisTemplate,
            new UlidPublicIdGenerator()
        );

        creatorUser = new CurrentUser(10L, "01CREATOR00000000000000000", "creator", "创作者", Set.of("CREATOR"), false);

        userEntity = new UserEntity();
        userEntity.setId(10L);
        userEntity.setUsername("creator");

        prototype = new PrototypeEntity();
        prototype.setId(100L);
        prototype.setPublicId("01PROTO000000000000000000");
        prototype.setCode("crm");
        prototype.setArchived(false);

        when(userRepository.findById(10L)).thenReturn(Optional.of(userEntity));
        when(prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototype.getPublicId()))
            .thenReturn(Optional.of(prototype));
        when(prototypeRepository.findByIdForUpdate(prototype.getId()))
            .thenReturn(Optional.of(prototype));
        when(authorizationService.canManage(any(), any())).thenReturn(true);
        when(authorizationService.canView(any(), any())).thenReturn(true);
    }

    @Test
    void activePublishBlocksNewUploadAndRollback() {
        when(publishJobRepository.existsByPrototypeIdAndStatusIn(eq(prototype.getId()), anyCollection()))
            .thenReturn(true);

        var createReq = new VersionDtos.CreateVersionRequest("upload-1", "ZIP", "更新版本", null);
        assertThatThrownBy(() -> service.createVersion(creatorUser, prototype.getPublicId(), createReq))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.PROTOTYPE_PUBLISH_IN_PROGRESS);

        var rollbackReq = new VersionDtos.SwitchCurrentVersionRequest(1, "修复回滚");
        assertThatThrownBy(() -> service.switchCurrent(creatorUser, prototype.getPublicId(), "ver-2", rollbackReq))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.PROTOTYPE_PUBLISH_IN_PROGRESS);
    }

    @Test
    void archivedPrototypeRejectsNewVersionAndRollback() {
        prototype.setArchived(true);
        when(publishJobRepository.existsByPrototypeIdAndStatusIn(eq(prototype.getId()), anyCollection()))
            .thenReturn(false);

        var createReq = new VersionDtos.CreateVersionRequest("upload-1", "ZIP", "更新版本", null);
        assertThatThrownBy(() -> service.createVersion(creatorUser, prototype.getPublicId(), createReq))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.PROTOTYPE_ARCHIVED);

        var rollbackReq = new VersionDtos.SwitchCurrentVersionRequest(1, "修复回滚");
        assertThatThrownBy(() -> service.switchCurrent(creatorUser, prototype.getPublicId(), "ver-2", rollbackReq))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.PROTOTYPE_ARCHIVED);
    }

    @Test
    void rollbackRejectsStaleExpectedCurrentVersion() {
        when(publishJobRepository.existsByPrototypeIdAndStatusIn(eq(prototype.getId()), anyCollection()))
            .thenReturn(false);

        PrototypeVersionEntity currentVersion = new PrototypeVersionEntity();
        currentVersion.setId(201L);
        currentVersion.setVersionNo(2);
        currentVersion.setStatus(VersionStatus.PUBLISHED);

        PrototypeVersionEntity targetVersion = new PrototypeVersionEntity();
        targetVersion.setId(200L);
        targetVersion.setPublicId("ver-1");
        targetVersion.setVersionNo(1);
        targetVersion.setStatus(VersionStatus.PUBLISHED);

        prototype.setCurrentVersionId(currentVersion.getId());
        when(versionRepository.findById(currentVersion.getId())).thenReturn(Optional.of(currentVersion));
        when(versionRepository.findByPrototypeIdAndPublicId(prototype.getId(), "ver-1")).thenReturn(Optional.of(targetVersion));

        // Stale expected version: expects 99, but actual is 2
        var staleReq = new VersionDtos.SwitchCurrentVersionRequest(99, "修复回滚");
        assertThatThrownBy(() -> service.switchCurrent(creatorUser, prototype.getPublicId(), "ver-1", staleReq))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.RESOURCE_VERSION_CONFLICT);

        // Correct expected version: 2
        var correctReq = new VersionDtos.SwitchCurrentVersionRequest(2, "正常回滚");
        var resp = service.switchCurrent(creatorUser, prototype.getPublicId(), "ver-1", correctReq);
        assertThat(resp.versionNo()).isEqualTo(1);
        assertThat(prototype.getCurrentVersionId()).isEqualTo(targetVersion.getId());
    }

    @Test
    void createVersionCopiesFileAndCreatesPendingPublishJob() {
        when(publishJobRepository.existsByPrototypeIdAndStatusIn(eq(prototype.getId()), anyCollection()))
            .thenReturn(false);
        when(versionRepository.findMaxVersionNoByPrototypeId(prototype.getId())).thenReturn(0);

        TemporaryUploadEntity upload = new TemporaryUploadEntity();
        upload.setPublicId("up-123");
        upload.setUser(userEntity);
        upload.setClaimedSize(512L);
        upload.setFileType("ZIP");
        upload.setObjectKey("temporary/up-123/source");
        upload.setChecksum("md5checksum");
        upload.setStatus("COMPLETED");

        when(uploadRepository.findByPublicId("up-123")).thenReturn(Optional.of(upload));
        when(objectStorage.open(upload.getObjectKey())).thenReturn(new ByteArrayInputStream(new byte[512]));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(publishJobRepository.save(any())).thenAnswer(inv -> {
            PublishJobEntity job = inv.getArgument(0);
            job.setId(999L);
            return job;
        });

        var req = new VersionDtos.CreateVersionRequest("up-123", "ZIP", "首次提交", "初始功能");
        var resp = service.createVersion(creatorUser, prototype.getPublicId(), req);

        assertThat(resp.versionNo()).isEqualTo(1);
        assertThat(resp.status()).isEqualTo("PUBLISHING");
        assertThat(resp.jobId()).isEqualTo(999L);
    }

    @Test
    void createVersionRejectsSourceTypeMismatch() {
        when(publishJobRepository.existsByPrototypeIdAndStatusIn(eq(prototype.getId()), anyCollection()))
            .thenReturn(false);

        TemporaryUploadEntity upload = new TemporaryUploadEntity();
        upload.setPublicId("up-html");
        upload.setUser(userEntity);
        upload.setClaimedSize(128L);
        upload.setFileType("HTML");
        upload.setStatus("COMPLETED");
        when(uploadRepository.findByPublicId("up-html")).thenReturn(Optional.of(upload));

        var req = new VersionDtos.CreateVersionRequest("up-html", "ZIP", "类型不一致", null);
        assertThatThrownBy(() -> service.createVersion(creatorUser, prototype.getPublicId(), req))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED);
    }
}
