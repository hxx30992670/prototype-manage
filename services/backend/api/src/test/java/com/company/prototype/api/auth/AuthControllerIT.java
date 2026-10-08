package com.company.prototype.api.auth;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIT {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService.resetRateLimits();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        userRepository.deleteAll();
        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));

        UserEntity user = new UserEntity();
        user.setPublicId("01ADMINUSER00000000000001");
        user.setUsername("testadmin");
        user.setPasswordHash(passwordEncoder.encode("Admin-Change-Me-123"));
        user.setDisplayName("测试管理员");
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setRoles(Set.of(adminRole));
        userRepository.save(user);

        UserEntity disabledUser = new UserEntity();
        disabledUser.setPublicId("01DISABLEDUSER00000000001");
        disabledUser.setUsername("disableduser");
        disabledUser.setPasswordHash(passwordEncoder.encode("Password-123"));
        disabledUser.setDisplayName("禁用用户");
        disabledUser.setStatus("DISABLED");
        disabledUser.setRoles(Set.of(adminRole));
        userRepository.save(disabledUser);
    }

    @Test
    void loginCreatesHostOnlyHttpOnlyRootPathCookieInHttpMode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString("PH_ADMIN_SESSION="),
                containsString("HttpOnly"),
                containsString("Path=/"),
                containsString("Max-Age=864000"),
                not(containsString("Domain=")),
                not(containsString("Secure"))
            )))
            .andExpect(jsonPath("$.data.username").value("testadmin"))
            .andExpect(jsonPath("$.data.mustChangePassword").value(true))
            .andReturn();

        assertEquals(864000, result.getRequest().getSession().getMaxInactiveInterval());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"WrongPassword"}
"""))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithDisabledAccountReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"disableduser","password":"Password-123"}
"""))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_DISABLED"));
    }

    @Test
    void meAndLogoutFlow() throws Exception {
        // 1. Unauthenticated /me returns 401
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());

        // 2. Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // 3. Authenticated /me
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("testadmin"));

        // 4. Logout
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        // 5. Subsequent /me returns 401
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordUpdatesPasswordAndClearsFlag() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(post("/api/v1/auth/change-password")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"oldPassword":"Admin-Change-Me-123","newPassword":"New-Admin-Password-456"}
"""))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        mockMvc.perform(get("/api/v1/prototypes").session(session))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"New-Admin-Password-456"}
"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    @Test
    void mustChangePasswordBlocksBusinessApisUntilPasswordChanged() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/prototypes").session(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void mustChangePasswordDoesNotBlockSubsequentLogin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(post("/api/v1/auth/login")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
{"username":"testadmin","password":"Admin-Change-Me-123"}
"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("testadmin"));
    }

    @Test
    void captchaEndpointReportsDisabledByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/auth/captcha"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false));
    }
}
