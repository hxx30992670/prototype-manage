package com.company.prototype.api.cleanup;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
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

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class StorageConsistencyJobIT {

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

    @MockitoBean
    private ObjectStorage objectStorage;

    @Autowired
    private CleanupScheduler cleanupScheduler;

    @Autowired
    private AdminConfigService configService;

    @Autowired
    private TemporaryUploadRepository temporaryUploadRepository;

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private CleanupTaskRepository cleanupTaskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UserEntity admin;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        cleanupTaskRepository.deleteAll();
        temporaryUploadRepository.deleteAll();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));

        admin = new UserEntity();
        admin.setPublicId("01ADMIN0000000000000000001");
        admin.setUsername("admin");
        admin.setPasswordHash("hashed");
        admin.setDisplayName("系统管理员");
        admin.setStatus("ACTIVE");
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        category = categoryRepository.save(new CategoryEntity("general", "通用", 0));

        // 默认所有对象存在
        when(objectStorage.stat(anyString())).thenAnswer(inv ->
            new ObjectStorage.ObjectMetadata(inv.getArgument(0), 10L, "hash", "application/octet-stream"));
    }

    @Test
    void scanCleansOrphanTemporaryUploadsOlderThan24Hours() {
        TemporaryUploadEntity orphan = new TemporaryUploadEntity();
        orphan.setPublicId("01ORPHAN0000000000000001");
        orphan.setUser(admin);
        orphan.setFilename("demo.zip");
        orphan.setFileType("ZIP");
        orphan.setObjectKey("temporary/01ORPHAN0000000000000001/source");
        orphan.setClaimedSize(100L);
        orphan.setStatus("PENDING");
        orphan.setExpiresAt(Instant.now().minusSeconds(3600));
        orphan.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
        temporaryUploadRepository.save(orphan);

        cleanupScheduler.consistencyScan();

        assertThat(temporaryUploadRepository.findByPublicId(orphan.getPublicId())).isEmpty();
    }

    @Test
    void scanCreatesPurgeTaskForExpiredRecycleBin() {
        PrototypeEntity expired = new PrototypeEntity();
        expired.setPublicId("01EXPIRED000000000000001");
        expired.setCode("EXPIRED");
        expired.setName("超期原型");
        expired.setCreatedBy(admin);
        expired.setOwner(admin);
        expired.setCategory(category);
        expired.setVisibility("ALL_INTERNAL");
        expired.setReviewStatus("DRAFT");
        expired.setDeletedAt(Instant.now().minusSeconds(60L * 24 * 3600));
        prototypeRepository.save(expired);

        cleanupScheduler.consistencyScan();

        CleanupTaskEntity task = cleanupTaskRepository.findAll().get(0);
        assertThat(task.getTaskType()).isEqualTo("PURGE_PROTOTYPE");
        assertThat(task.getTargetId()).isEqualTo(expired.getPublicId());
        assertThat(task.getStatus()).isEqualTo(CleanupTaskStatus.PENDING);
    }

    @Test
    void scanDoesNotPurgePrototypeStillInsideRetentionWindow() {
        PrototypeEntity recent = new PrototypeEntity();
        recent.setPublicId("01RECENT0000000000000001");
        recent.setCode("RECENT");
        recent.setName("近期原型");
        recent.setCreatedBy(admin);
        recent.setOwner(admin);
        recent.setCategory(category);
        recent.setVisibility("ALL_INTERNAL");
        recent.setReviewStatus("DRAFT");
        recent.setDeletedAt(Instant.now().minusSeconds(3600));
        prototypeRepository.save(recent);

        cleanupScheduler.consistencyScan();

        assertThat(cleanupTaskRepository.findAll()).isEmpty();
    }

    @Test
    void scanReportsMissingObjectsWithoutDeletingRecords() {
        PrototypeEntity prototype = new PrototypeEntity();
        prototype.setPublicId("01MISSING00000000000001");
        prototype.setCode("MISSING");
        prototype.setName("对象缺失原型");
        prototype.setCreatedBy(admin);
        prototype.setOwner(admin);
        prototype.setCategory(category);
        prototype.setVisibility("ALL_INTERNAL");
        prototype.setReviewStatus("DRAFT");
        prototype.setCoverObjectKey("prototypes/1/covers/1/original");
        prototypeRepository.save(prototype);

        // 封面对象缺失
        when(objectStorage.stat("prototypes/1/covers/1/original")).thenReturn(null);

        cleanupScheduler.consistencyScan();

        // 记录未被删除，任务未被创建（缺失对象只报告不自动清理）
        assertThat(prototypeRepository.findById(prototype.getId())).isPresent();
        assertThat(cleanupTaskRepository.findAll()).isEmpty();
    }
}
