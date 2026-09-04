package com.company.prototype.api.upload;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class UploadServiceTest {

    private InMemoryObjectStorage storage;
    private TemporaryUploadRepository uploadRepository;
    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UploadService service;

    private CurrentUser owner;
    private CurrentUser otherUser;
    private UserEntity ownerEntity;
    private MutableClock clock;

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        public void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static class InMemoryObjectStorage implements ObjectStorage {
        private final Map<String, ObjectMetadata> metadataMap = new HashMap<>();
        private int presignCallCount = 0;

        void stubMetadata(String objectKey, long size, String checksum) {
            metadataMap.put(objectKey, new ObjectMetadata(objectKey, size, checksum, "application/zip"));
        }

        @Override
        public PresignedUpload createUpload(String objectKey, long maxBytes, Duration ttl) {
            presignCallCount++;
            return new PresignedUpload(
                "http://prototype.corp.test/upload-objects/prototype-objects/" + objectKey + "?v=" + presignCallCount,
                objectKey,
                Instant.now().plus(ttl)
            );
        }

        @Override
        public ObjectMetadata stat(String objectKey) {
            return metadataMap.get(objectKey);
        }

        @Override
        public InputStream open(String objectKey) {
            return InputStream.nullInputStream();
        }

        @Override
        public void put(String objectKey, InputStream input, long size, String contentType) {
            stubMetadata(objectKey, size, "hash");
        }

        @Override
        public void deletePrefix(String prefix) {
            metadataMap.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    @BeforeEach
    void setUp() {
        storage = new InMemoryObjectStorage();
        uploadRepository = Mockito.mock(TemporaryUploadRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        owner = new CurrentUser(1L, "01M1OWNER00000000000000000", "owner", "所有者", Set.of("CREATOR"), false);
        otherUser = new CurrentUser(2L, "01M1OTHER0000000000000000", "other", "其他人", Set.of("CREATOR"), false);

        ownerEntity = new UserEntity();
        ownerEntity.setId(1L);
        ownerEntity.setPublicId("01M1OWNER00000000000000000");
        ownerEntity.setUsername("owner");

        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerEntity));

        clock = new MutableClock(Instant.parse("2026-09-01T12:00:00Z"));

        service = new UploadService(
            storage,
            uploadRepository,
            userRepository,
            new UlidPublicIdGenerator(),
            redisTemplate,
            null // AdminConfigService：单测不启用配置服务，使用内置默认值
        );
        service.setClock(clock);
    }

    @Test
    void completeRejectsMismatchedSizeOrOwner() {
        var createReq = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        var uploadRes = service.create(owner, createReq, null);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(uploadRes.uploadId());
        entity.setUser(ownerEntity);
        entity.setClaimedSize(1024L);
        entity.setObjectKey("temporary/" + uploadRes.uploadId() + "/source");
        entity.setFileType("ZIP");
        entity.setExpiresAt(uploadRes.expiresAt());
        when(uploadRepository.findByPublicId(uploadRes.uploadId())).thenReturn(Optional.of(entity));

        storage.stubMetadata(entity.getObjectKey(), 2048L, "abc");

        assertThatThrownBy(() -> service.complete(otherUser, uploadRes.uploadId(), "abc"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);

        assertThatThrownBy(() -> service.complete(owner, uploadRes.uploadId(), "abc"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_SIZE_MISMATCH);
    }

    @Test
    void repeatedIdempotencyKeyKeepsUploadIdButRefreshesExpiredUrl() {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        String idemKey = "key-1";
        String redisKey = "idempotency:upload:1:key-1";

        var first = service.create(owner, req, idemKey);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(first.uploadId());
        entity.setUser(ownerEntity);
        entity.setFilename("demo.zip");
        entity.setFileType("ZIP");
        entity.setClaimedSize(1024L);
        entity.setObjectKey("temporary/" + first.uploadId() + "/source");
        entity.setExpiresAt(first.expiresAt());
        when(uploadRepository.findByPublicId(first.uploadId())).thenReturn(Optional.of(entity));

        when(valueOperations.get(redisKey)).thenReturn(first.uploadId());

        clock.advance(Duration.ofMinutes(16));

        var second = service.create(owner, req, idemKey);

        assertThat(second.uploadId()).isEqualTo(first.uploadId());
        assertThat(second.uploadUrl()).isNotEqualTo(first.uploadUrl());
    }

    @Test
    void completeRejectsExpiredUpload() {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        var res = service.create(owner, req, null);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(res.uploadId());
        entity.setUser(ownerEntity);
        entity.setClaimedSize(1024L);
        entity.setObjectKey("temporary/" + res.uploadId() + "/source");
        entity.setExpiresAt(clock.instant().minus(Duration.ofSeconds(1)));
        when(uploadRepository.findByPublicId(res.uploadId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.complete(owner, res.uploadId(), null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_EXPIRED);
    }

    @Test
    void completeRejectsMissingObject() {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        var res = service.create(owner, req, null);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(res.uploadId());
        entity.setUser(ownerEntity);
        entity.setClaimedSize(1024L);
        entity.setObjectKey("temporary/" + res.uploadId() + "/source");
        entity.setExpiresAt(clock.instant().plus(Duration.ofMinutes(10)));
        when(uploadRepository.findByPublicId(res.uploadId())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.complete(owner, res.uploadId(), null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_OBJECT_MISSING);
    }

    @Test
    void completeRejectsMismatchedChecksum() {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        var res = service.create(owner, req, null);

        TemporaryUploadEntity entity = new TemporaryUploadEntity();
        entity.setPublicId(res.uploadId());
        entity.setUser(ownerEntity);
        entity.setClaimedSize(1024L);
        entity.setObjectKey("temporary/" + res.uploadId() + "/source");
        entity.setExpiresAt(clock.instant().plus(Duration.ofMinutes(10)));
        when(uploadRepository.findByPublicId(res.uploadId())).thenReturn(Optional.of(entity));

        storage.stubMetadata(entity.getObjectKey(), 1024L, "correct_checksum");

        assertThatThrownBy(() -> service.complete(owner, res.uploadId(), "wrong_checksum"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_CHECKSUM_MISMATCH);
    }

    @Test
    void createRejectsOversizedHtmlAndZip() {
        // 默认限制：HTML 20MB、ZIP 200MB（超出默认值即拒绝）
        assertThatThrownBy(() -> service.create(owner, new UploadDtos.CreateUploadRequest("index.html", 21 * 1024 * 1024L, "HTML"), null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_HTML_TOO_LARGE);

        assertThatThrownBy(() -> service.create(owner, new UploadDtos.CreateUploadRequest("archive.zip", 201 * 1024 * 1024L, "ZIP"), null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.UPLOAD_ZIP_TOO_LARGE);
    }
}
