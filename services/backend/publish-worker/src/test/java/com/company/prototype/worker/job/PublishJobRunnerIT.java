package com.company.prototype.worker.job;

import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.PublishJobEntity;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.PublishJobStatus;
import com.company.prototype.persistence.publish.PublishStage;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
import com.company.prototype.worker.PublishWorkerApplication;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PublishWorkerApplication.class)
@ActiveProfiles("test")
@Testcontainers
class PublishJobRunnerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        mysql.start();
        Flyway.configure()
            .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private PublishJobRunner runner;

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private PrototypeVersionRepository versionRepository;

    @Autowired
    private PublishJobRepository publishJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    private UserEntity testUser;
    private CategoryEntity testCategory;
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();
    private final Map<String, byte[]> storageBytes = new HashMap<>();

    @BeforeEach
    void setUp() {
        publishJobRepository.deleteAll();
        prototypeRepository.findAll().forEach(p -> {
            p.setCurrentVersionId(null);
            prototypeRepository.save(p);
        });
        prototypeRepository.flush();
        versionRepository.deleteAll();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        storageBytes.clear();

        testCategory = new CategoryEntity();
        testCategory.setCode("crm");
        testCategory.setName("客户系统");
        testCategory = categoryRepository.save(testCategory);

        testUser = new UserEntity();
        testUser.setPublicId(idGenerator.nextId());
        testUser.setUsername("author");
        testUser.setPasswordHash("hash");
        testUser.setDisplayName("作者");
        testUser = userRepository.save(testUser);

        when(objectStorage.stat(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            byte[] data = storageBytes.get(key);
            if (data == null) return null;
            return new ObjectStorage.ObjectMetadata(key, data.length, "hash", "application/octet-stream");
        });

        when(objectStorage.open(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            byte[] data = storageBytes.get(key);
            if (data == null) throw new RuntimeException("Key not found: " + key);
            return new ByteArrayInputStream(data);
        });
    }

    @Test
    void failedPublishDoesNotChangeCurrentVersion() throws Exception {
        // 1. Initial prototype with published current version
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(idGenerator.nextId());
        proto.setCode("proto-v1");
        proto.setName("测试原型");
        proto.setCategory(testCategory);
        proto.setCreatedBy(testUser);
        proto.setOwner(testUser);
        proto = prototypeRepository.save(proto);

        PrototypeVersionEntity oldVersion = new PrototypeVersionEntity();
        oldVersion.setPublicId(idGenerator.nextId());
        oldVersion.setPrototype(proto);
        oldVersion.setVersionNo(1);
        oldVersion.setChangeLog("v1");
        oldVersion.setStatus(VersionStatus.PUBLISHED);
        oldVersion.setSourceType("HTML");
        oldVersion.setSourceObjectKey("source/v1.html");
        oldVersion.setChecksum("hash1");
        oldVersion.setSourceSize(100L);
        oldVersion.setCreatedBy(testUser);
        oldVersion = versionRepository.save(oldVersion);

        proto.setCurrentVersionId(oldVersion.getId());
        proto = prototypeRepository.save(proto);

        // 2. New version with malicious zip slip
        PrototypeVersionEntity evilVersion = new PrototypeVersionEntity();
        evilVersion.setPublicId(idGenerator.nextId());
        evilVersion.setPrototype(proto);
        evilVersion.setVersionNo(2);
        evilVersion.setChangeLog("v2 evil");
        evilVersion.setStatus(VersionStatus.PUBLISHING);
        evilVersion.setSourceType("ZIP");
        evilVersion.setSourceObjectKey("source/evil.zip");
        evilVersion.setChecksum("hash2");
        evilVersion.setSourceSize(200L);
        evilVersion.setCreatedBy(testUser);
        evilVersion = versionRepository.save(evilVersion);

        byte[] evilZip = createZipWithEntry("../escape.txt", "attack");
        storageBytes.put(evilVersion.getSourceObjectKey(), evilZip);

        PublishJobEntity job = new PublishJobEntity();
        job.setPrototype(proto);
        job.setVersion(evilVersion);
        job.setStatus(PublishJobStatus.PENDING);
        job.setStage(PublishStage.UPLOAD_CHECK);
        job = publishJobRepository.save(job);

        // Run
        runner.run(job.getId());

        // Verify: currentVersionId remains oldVersion, evilVersion status is FAILED
        PrototypeEntity refreshedProto = prototypeRepository.findById(proto.getId()).orElseThrow();
        assertThat(refreshedProto.getCurrentVersionId()).isEqualTo(oldVersion.getId());

        PrototypeVersionEntity refreshedVersion = versionRepository.findById(evilVersion.getId()).orElseThrow();
        assertThat(refreshedVersion.getStatus()).isEqualTo(VersionStatus.FAILED);
        assertThat(refreshedVersion.getFailureStage()).isEqualTo("EXTRACT");

        PublishJobEntity refreshedJob = publishJobRepository.findById(job.getId()).orElseThrow();
        assertThat(refreshedJob.getStatus()).isEqualTo(PublishJobStatus.FAILED);
    }

    @Test
    void successfulPublishUpdatesCurrentVersionAndUploadsFiles() throws Exception {
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(idGenerator.nextId());
        proto.setCode("proto-success");
        proto.setName("测试原型成功");
        proto.setCategory(testCategory);
        proto.setCreatedBy(testUser);
        proto.setOwner(testUser);
        proto = prototypeRepository.save(proto);

        PrototypeVersionEntity version = new PrototypeVersionEntity();
        version.setPublicId(idGenerator.nextId());
        version.setPrototype(proto);
        version.setVersionNo(1);
        version.setChangeLog("v1 release");
        version.setStatus(VersionStatus.PUBLISHING);
        version.setSourceType("ZIP");
        version.setSourceObjectKey("source/good.zip");
        version.setChecksum("hashgood");
        version.setSourceSize(300L);
        version.setCreatedBy(testUser);
        version = versionRepository.save(version);

        byte[] goodZip = createZipWithEntry("index.html", "<html><body>Success</body></html>");
        storageBytes.put(version.getSourceObjectKey(), goodZip);

        PublishJobEntity job = new PublishJobEntity();
        job.setPrototype(proto);
        job.setVersion(version);
        job.setStatus(PublishJobStatus.PENDING);
        job.setStage(PublishStage.UPLOAD_CHECK);
        job = publishJobRepository.save(job);

        // Run
        runner.run(job.getId());

        // Verify
        PublishJobEntity refreshedJob = publishJobRepository.findById(job.getId()).orElseThrow();
        PrototypeEntity refreshedProto = prototypeRepository.findById(proto.getId()).orElseThrow();
        assertThat(refreshedProto.getCurrentVersionId()).isEqualTo(version.getId());

        PrototypeVersionEntity refreshedVersion = versionRepository.findById(version.getId()).orElseThrow();
        assertThat(refreshedVersion.getStatus()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(refreshedVersion.getEntryPath()).isEqualTo("index.html");
        assertThat(refreshedVersion.getFileCount()).isEqualTo(1);

        assertThat(refreshedJob.getStatus()).isEqualTo(PublishJobStatus.SUCCEEDED);
        assertThat(refreshedJob.getProgress()).isEqualTo(100);
    }

    private byte[] createZipWithEntry(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            entry.setSize(bytes.length);
            zos.putArchiveEntry(entry);
            zos.write(bytes);
            zos.closeArchiveEntry();
        }
        return baos.toByteArray();
    }
}
