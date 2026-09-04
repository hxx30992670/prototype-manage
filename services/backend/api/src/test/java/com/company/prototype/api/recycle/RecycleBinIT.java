package com.company.prototype.api.recycle;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.api.cleanup.PurgeTaskExecutor;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.share.PrototypeShareLinkEntity;
import com.company.prototype.persistence.share.PrototypeShareLinkRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class RecycleBinIT {

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
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private PrototypeShareLinkRepository shareLinkRepository;

    @Autowired
    private CleanupTaskRepository cleanupTaskRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PurgeTaskExecutor purgeTaskExecutor;

    private MockMvc mockMvc;
    private UserEntity admin;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        auditLogRepository.deleteAll();
        cleanupTaskRepository.deleteAll();
        shareLinkRepository.deleteAll();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));
        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "原型创建者")));
        RoleEntity viewerRole = roleRepository.findByCode("VIEWER")
            .orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "普通查看者")));

        admin = new UserEntity();
        admin.setPublicId("01ADMIN0000000000000000001");
        admin.setUsername("admin");
        admin.setPasswordHash("hashed");
        admin.setDisplayName("系统管理员");
        admin.setStatus("ACTIVE");
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        UserEntity viewer = new UserEntity();
        viewer.setPublicId("01VIEWER000000000000000001");
        viewer.setUsername("viewer");
        viewer.setPasswordHash("hashed");
        viewer.setDisplayName("普通查看者");
        viewer.setStatus("ACTIVE");
        viewer.setRoles(Set.of(viewerRole));
        userRepository.save(viewer);

        category = categoryRepository.save(new CategoryEntity("general", "通用", 0));
    }

    private PrototypeEntity createPrototype(String code) {
        PrototypeEntity p = new PrototypeEntity();
        p.setPublicId("01PROTO" + code + "00000000000000");
        p.setCode(code);
        p.setName("原型 " + code);
        p.setCreatedBy(admin);
        p.setOwner(admin);
        p.setCategory(category);
        p.setVisibility("ALL_INTERNAL");
        p.setReviewStatus("DRAFT");
        return prototypeRepository.save(p);
    }

    private PrototypeShareLinkEntity createShare(PrototypeEntity prototype) {
        PrototypeShareLinkEntity link = new PrototypeShareLinkEntity();
        link.setPublicId("01SHARE" + prototype.getCode() + "00000000000");
        link.setPrototype(prototype);
        link.setName("评审分享");
        link.setTokenDigest("digest-" + prototype.getCode());
        link.setStatus("ACTIVE");
        link.setSpecScope("SUMMARY");
        link.setAllowComment(true);
        link.setCreatedBy(admin);
        return shareLinkRepository.save(link);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deleteDisablesSharesAndIncrementsAuthEpoch() throws Exception {
        PrototypeEntity prototype = createPrototype("DEL01");
        PrototypeShareLinkEntity link = createShare(prototype);
        assertThat(link.getAuthEpoch()).isZero();

        mockMvc.perform(delete("/api/v1/prototypes/" + prototype.getPublicId()).with(csrf()))
            .andExpect(status().isOk());

        PrototypeShareLinkEntity after = shareLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("DISABLED");
        assertThat(after.getAuthEpoch()).isEqualTo(1);
        assertThat(prototypeRepository.findByPublicIdAndDeletedAtIsNotNull(prototype.getPublicId())).isPresent();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void restoreKeepsSharesDisabled() throws Exception {
        PrototypeEntity prototype = createPrototype("RES01");
        PrototypeShareLinkEntity link = createShare(prototype);

        mockMvc.perform(delete("/api/v1/prototypes/" + prototype.getPublicId()).with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/prototypes/" + prototype.getPublicId() + "/restore").with(csrf()))
            .andExpect(status().isOk());

        assertThat(prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototype.getPublicId())).isPresent();
        PrototypeShareLinkEntity after = shareLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void purgeCreatesCleanupTask() throws Exception {
        PrototypeEntity prototype = createPrototype("PRG01");
        mockMvc.perform(delete("/api/v1/prototypes/" + prototype.getPublicId()).with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/prototypes/" + prototype.getPublicId() + "/purge").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskId").isNumber());

        CleanupTaskEntity task = cleanupTaskRepository.findAll().get(0);
        assertThat(task.getTaskType()).isEqualTo("PURGE_PROTOTYPE");
        assertThat(task.getTargetId()).isEqualTo(prototype.getPublicId());
        assertThat(task.getStatus()).isEqualTo(CleanupTaskStatus.PENDING);
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void viewerCannotRestoreOrPurge() throws Exception {
        PrototypeEntity prototype = createPrototype("VW01");
        mockMvc.perform(post("/api/v1/admin/prototypes/" + prototype.getPublicId() + "/restore").with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/prototypes/" + prototype.getPublicId() + "/purge").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void purgeFailureKeepsRetryableTaskAndDoesNotLoseAuditTrail() throws Exception {
        PrototypeEntity prototype = createPrototype("RTY01");
        mockMvc.perform(delete("/api/v1/prototypes/" + prototype.getPublicId()).with(csrf()))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/prototypes/" + prototype.getPublicId() + "/purge").with(csrf()))
            .andExpect(status().isOk());

        CleanupTaskEntity task = cleanupTaskRepository.findAll().get(0);

        // 第一次执行：对象删除失败 → RETRYING，审计保留
        doThrow(new RuntimeException("MinIO 临时故障")).when(objectStorage).deletePrefix(anyString());
        purgeTaskExecutor.run(task.getId());
        CleanupTaskEntity retried = cleanupTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(CleanupTaskStatus.RETRYING);
        assertThat(retried.getAttemptCount()).isEqualTo(1);
        assertThat(auditLogRepository.findAll().stream().map(AuditLogEntity::getAction))
            .contains("PROTOTYPE_PURGE");

        // 第二次执行：存储恢复 → SUCCEEDED，业务记录被删除
        org.mockito.Mockito.reset(objectStorage);
        purgeTaskExecutor.run(task.getId());
        CleanupTaskEntity done = cleanupTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(CleanupTaskStatus.SUCCEEDED);
        assertThat(prototypeRepository.findById(prototype.getId())).isEmpty();
        assertThat(shareLinkRepository.findAllByPrototypeIdOrderByCreatedAtDesc(prototype.getId())).isEmpty();
    }
}
