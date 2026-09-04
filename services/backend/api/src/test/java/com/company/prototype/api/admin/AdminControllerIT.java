package com.company.prototype.api.admin;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.prototype.TagEntity;
import com.company.prototype.persistence.prototype.TagRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class AdminControllerIT {

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
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        auditLogRepository.deleteAll();
        prototypeRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();

        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));
        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "原型创建者")));
        roleRepository.findByCode("VIEWER")
            .orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "普通查看者")));

        UserEntity admin = new UserEntity();
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
        viewer.setRoles(Set.of(roleRepository.findByCode("VIEWER").orElseThrow()));
        userRepository.save(viewer);

        UserEntity creator = new UserEntity();
        creator.setPublicId("01CREATOR00000000000000001");
        creator.setUsername("creator");
        creator.setPasswordHash("hashed");
        creator.setDisplayName("原型创建者");
        creator.setStatus("ACTIVE");
        creator.setRoles(Set.of(creatorRole));
        userRepository.save(creator);
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void viewerCannotChangeConfig() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/upload.zip.maxMb")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"value": "200"}
                """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanChangeConfigAndEveryChangeIsAudited() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/upload.zip.maxMb")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"value": "200"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value("200"));

        AuditLogEntity latest = auditLogRepository.findTop10ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getAction()).isEqualTo("SYSTEM_CONFIG_UPDATE");
        assertThat(latest.getResult()).isEqualTo("SUCCESS");
        assertThat(latest.getTargetId()).isEqualTo("upload.zip.maxMb");
        assertThat(latest.getSummary()).doesNotContain("password", "token");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void configUpdateRejectsValuesBeyondHardMaximum() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/upload.zip.maxMb")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"value": "9999"}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CONFIG_OUT_OF_RANGE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void configListReturnsAllItemsWithDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/admin/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[?(@.key=='upload.zip.maxMb')].value").value("200"))
            .andExpect(jsonPath("$.data[?(@.key=='upload.zip.maxMb')].hardMaximum").value(1000))
            .andExpect(jsonPath("$.data[?(@.key=='recycle.retentionDays')].value").value("30"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void tagMergeRebindsPrototypesAndAudits() throws Exception {
        TagEntity source = tagRepository.save(new TagEntity("旧标签", "#FF0000"));
        TagEntity target = tagRepository.save(new TagEntity("新标签", "#00FF00"));
        UserEntity admin = userRepository.findByUsername("admin").orElseThrow();

        CategoryEntity category = categoryRepository.save(new CategoryEntity("general", "通用", 0));

        PrototypeEntity prototype = new PrototypeEntity();
        prototype.setPublicId("01TAGPROTOTYPE00000000001");
        prototype.setCode("TAG-DEMO");
        prototype.setName("标签演示原型");
        prototype.setCreatedBy(admin);
        prototype.setOwner(admin);
        prototype.setCategory(category);
        prototype.setVisibility("ALL_INTERNAL");
        prototype.setReviewStatus("DRAFT");
        prototype.setTags(Set.of(source));
        prototypeRepository.save(prototype);

        mockMvc.perform(post("/api/v1/admin/tags/" + source.getName() + "/merge")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"targetTagName": "新标签"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.affectedPrototypeCount").value(1));

        assertThat(tagRepository.findByName("旧标签")).isEmpty();
        assertThat(tagRepository.countPrototypesByTag(target.getId())).isEqualTo(1);

        AuditLogEntity latest = auditLogRepository.findTop10ByOrderByCreatedAtDesc().get(0);
        assertThat(latest.getAction()).isEqualTo("TAG_MERGE");
        assertThat(latest.getSummary()).contains("影响原型 1 个");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void auditLogsQueryableByAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .param("action", "LOGIN_SUCCESS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void viewerCannotQueryAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
            .andExpect(status().isForbidden());
    }
}
