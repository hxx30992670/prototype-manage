package com.company.prototype.api.upload;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class UploadControllerIT {

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
    private TemporaryUploadRepository uploadRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ObjectStorage objectStorage;

    private MockMvc mockMvc;
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();
    private UserEntity creatorUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        uploadRepository.deleteAll();
        userRepository.deleteAll();

        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创作者")));

        creatorUser = new UserEntity();
        creatorUser.setPublicId(idGenerator.nextId());
        creatorUser.setUsername("creator_test");
        creatorUser.setPasswordHash("hash");
        creatorUser.setDisplayName("创作者用户");
        creatorUser.setRoles(Set.of(creatorRole));
        creatorUser = userRepository.save(creatorUser);

        when(objectStorage.createUpload(anyString(), anyLong(), any(Duration.class)))
            .thenAnswer(inv -> new ObjectStorage.PresignedUpload(
                "http://prototype.corp.test/upload-objects/prototype-objects/" + inv.getArgument(0),
                inv.getArgument(0),
                Instant.now().plus(Duration.ofMinutes(15))
            ));
    }

    @Test
    void unauthenticatedRequestToCreateUploadReturnsUnauthorized() throws Exception {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 1024L, "ZIP");
        mockMvc.perform(post("/api/v1/uploads")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "creator_test", roles = {"CREATOR"})
    void authenticatedUserCanCreateUpload() throws Exception {
        var req = new UploadDtos.CreateUploadRequest("demo.zip", 2048L, "ZIP");

        mockMvc.perform(post("/api/v1/uploads")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.uploadId").isNotEmpty())
            .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.startsWith("http://prototype.corp.test/upload-objects/prototype-objects/temporary/")));
    }

    @Test
    @WithMockUser(username = "creator_test", roles = {"CREATOR"})
    void completeUploadVerifiesSizeAndObject() throws Exception {
        var req = new UploadDtos.CreateUploadRequest("test.html", 512L, "HTML");

        String resStr = mockMvc.perform(post("/api/v1/uploads")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String uploadId = objectMapper.readTree(resStr).path("data").path("uploadId").asText();
        String objectKey = "temporary/" + uploadId + "/source";

        // Object missing
        when(objectStorage.stat(objectKey)).thenReturn(null);
        mockMvc.perform(post("/api/v1/uploads/" + uploadId + "/complete")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UPLOAD_OBJECT_MISSING"));

        // Size match and success
        when(objectStorage.stat(objectKey)).thenReturn(new ObjectStorage.ObjectMetadata(objectKey, 512L, "etag123", "text/html"));
        mockMvc.perform(post("/api/v1/uploads/" + uploadId + "/complete")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UploadDtos.CompleteUploadRequest("etag123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.actualSize").value(512));
    }
}
