package com.company.prototype.persistence;

import com.company.prototype.common.id.UlidPublicIdGenerator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootTest(classes = VersionMigrationIT.TestApplication.class)
@Testcontainers
@ActiveProfiles("test")
public class VersionMigrationIT {

    @SpringBootApplication(scanBasePackages = "com.company.prototype.persistence")
    static class TestApplication {}

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private PrototypeVersionRepository versionRepository;

    @Autowired
    private PublishJobRepository publishJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.company.prototype.persistence.prototype.CategoryRepository categoryRepository;

    private UserEntity testUser;
    private PrototypeEntity testPrototype;
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    @BeforeEach
    void setUp() {
        publishJobRepository.deleteAll();
        versionRepository.deleteAll();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        com.company.prototype.persistence.prototype.CategoryEntity cat = new com.company.prototype.persistence.prototype.CategoryEntity();
        cat.setCode("crm");
        cat.setName("客户关系");
        cat = categoryRepository.save(cat);

        testUser = new UserEntity();
        testUser.setPublicId(idGenerator.nextId());
        testUser.setUsername("designer");
        testUser.setPasswordHash("hash");
        testUser.setDisplayName("设计师");
        testUser = userRepository.save(testUser);

        testPrototype = new PrototypeEntity();
        testPrototype.setPublicId(idGenerator.nextId());
        testPrototype.setCode("crm-proto");
        testPrototype.setName("CRM系统");
        testPrototype.setCategory(cat);
        testPrototype.setCreatedBy(testUser);
        testPrototype.setOwner(testUser);
        testPrototype = prototypeRepository.save(testPrototype);
    }

    @Test
    void onlyOneActivePublishJobCanExistPerPrototype() {
        PrototypeVersionEntity version1 = createVersion(1);
        PrototypeVersionEntity version2 = createVersion(2);

        PublishJobEntity job1 = new PublishJobEntity();
        job1.setPrototype(testPrototype);
        job1.setVersion(version1);
        job1.setStatus(PublishJobStatus.RUNNING);
        job1.setStage(PublishStage.EXTRACT);
        job1.setCreatedAt(Instant.now());
        publishJobRepository.saveAndFlush(job1);

        PublishJobEntity job2 = new PublishJobEntity();
        job2.setPrototype(testPrototype);
        job2.setVersion(version2);
        job2.setStatus(PublishJobStatus.PENDING);
        job2.setStage(PublishStage.UPLOAD_CHECK);
        job2.setCreatedAt(Instant.now());

        assertThatThrownBy(() -> publishJobRepository.saveAndFlush(job2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private PrototypeVersionEntity createVersion(int versionNo) {
        PrototypeVersionEntity v = new PrototypeVersionEntity();
        v.setPublicId(idGenerator.nextId());
        v.setPrototype(testPrototype);
        v.setVersionNo(versionNo);
        v.setChangeLog("初始版本");
        v.setStatus(VersionStatus.PUBLISHING);
        v.setSourceType("ZIP");
        v.setSourceObjectKey("source/" + v.getPublicId() + ".zip");
        v.setChecksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        v.setSourceSize(1024L);
        v.setCreatedBy(testUser);
        v.setCreatedAt(Instant.now());
        return versionRepository.saveAndFlush(v);
    }
}
