package com.company.prototype.api.user;

import com.company.prototype.api.PrototypeApiApplication;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class UserAdminControllerIT {

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

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        userRepository.deleteAll();
        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));
        roleRepository.findByCode("CREATOR")
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
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanListAndCreateUsers() throws Exception {
        // 1. List users
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.pagination.total").value(1));

        // 2. Create user
        mockMvc.perform(post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{
  "username": "newcreator",
  "password": "Password-123456",
  "displayName": "新设计人员",
  "department": "体验设计部",
  "roles": ["CREATOR"]
}
"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.username").value("newcreator"))
            .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        // 3. Verify total is now 2
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total").value(2));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCannotAccessUserAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanUpdateStatusAndResetPassword() throws Exception {
        // Create user
        UserEntity target = new UserEntity();
        target.setPublicId("01TARGET00000000000000001");
        target.setUsername("targetuser");
        target.setPasswordHash("hashed");
        target.setDisplayName("目标用户");
        target.setStatus("ACTIVE");
        target = userRepository.save(target);

        // 1. Update status to DISABLED
        mockMvc.perform(put("/api/v1/admin/users/" + target.getPublicId() + "/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"status": "DISABLED"}
"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 2. Reset password
        mockMvc.perform(post("/api/v1/admin/users/" + target.getPublicId() + "/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"newPassword": "Reset-Password-888"}
"""))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanChangeOtherUsersRolesIncludingOtherAdmins() throws Exception {
        RoleEntity adminRole = roleRepository.findByCode("ADMIN").orElseThrow();
        UserEntity otherAdmin = new UserEntity();
        otherAdmin.setPublicId("01OTHERADMIN0000000000001");
        otherAdmin.setUsername("opsadmin");
        otherAdmin.setPasswordHash("hashed");
        otherAdmin.setDisplayName("运维管理员");
        otherAdmin.setStatus("ACTIVE");
        otherAdmin.setRoles(Set.of(adminRole));
        otherAdmin = userRepository.save(otherAdmin);

        mockMvc.perform(put("/api/v1/admin/users/" + otherAdmin.getPublicId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{
  "displayName": "运维管理员",
  "department": "运维",
  "roles": ["CREATOR"]
}
"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles", hasItem("CREATOR")))
            .andExpect(jsonPath("$.data.roles", not(hasItem("ADMIN"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void bootstrapAdminCannotBeModified() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/01ADMIN0000000000000000001")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"displayName":"被改名","roles":["VIEWER"]}
"""))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/v1/admin/users/01ADMIN0000000000000000001/roles")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"roles":["VIEWER"]}
"""))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/users/01ADMIN0000000000000000001/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"status":"DISABLED"}
"""))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users/01ADMIN0000000000000000001/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"newPassword":"Hacked-Password-1"}
"""))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void listMarksBootstrapAdminAsProtected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].username").value("admin"))
            .andExpect(jsonPath("$.data[0].protectedAccount").value(true));
    }
}
