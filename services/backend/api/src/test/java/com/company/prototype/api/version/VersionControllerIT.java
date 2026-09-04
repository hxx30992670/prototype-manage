package com.company.prototype.api.version;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.persistence.version.VersionStatus;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class VersionControllerIT {

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
    private PrototypeRepository prototypeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PrototypeVersionRepository versionRepository;

    @Autowired
    private PublishJobRepository publishJobRepository;

    @Autowired
    private TemporaryUploadRepository uploadRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    private UserEntity creatorUser;
    private PrototypeEntity prototype;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        publishJobRepository.deleteAll();
        prototypeRepository.findAll().forEach(p -> {
            p.setCurrentVersionId(null);
            prototypeRepository.save(p);
        });
        prototypeRepository.flush();
        versionRepository.deleteAll();
        prototypeRepository.deleteAll();
        uploadRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创作者")));

        creatorUser = new UserEntity();
        creatorUser.setPublicId(idGenerator.nextId());
        creatorUser.setUsername("creator_user");
        creatorUser.setPasswordHash("hash");
        creatorUser.setDisplayName("创作者");
        creatorUser.setRoles(Set.of(creatorRole));
        creatorUser = userRepository.save(creatorUser);

        category = new CategoryEntity();
        category.setCode("crm");
        category.setName("客户关系");
        category = categoryRepository.save(category);

        prototype = new PrototypeEntity();
        prototype.setPublicId(idGenerator.nextId());
        prototype.setCode("crm-proto");
        prototype.setName("CRM系统原型");
        prototype.setCategory(category);
        prototype.setCreatedBy(creatorUser);
        prototype.setOwner(creatorUser);
        prototype = prototypeRepository.save(prototype);

        when(objectStorage.open(anyString())).thenAnswer(inv -> new ByteArrayInputStream("fake content".getBytes(StandardCharsets.UTF_8)));
        when(objectStorage.stat(anyString())).thenAnswer(inv -> new ObjectStorage.ObjectMetadata(inv.getArgument(0), 12L, "hash", "text/html"));
    }

    @Test
    @WithMockUser(username = "creator_user", roles = {"CREATOR"})
    void createAndListVersions() throws Exception {
        TemporaryUploadEntity upload = new TemporaryUploadEntity();
        upload.setPublicId(idGenerator.nextId());
        upload.setUser(creatorUser);
        upload.setFilename("v1.zip");
        upload.setFileType("ZIP");
        upload.setObjectKey("temporary/" + upload.getPublicId() + "/source");
        upload.setClaimedSize(1024L);
        upload.setChecksum("hash123");
        upload.setStatus("COMPLETED");
        upload.setExpiresAt(Instant.now().plusSeconds(3600));
        upload.setCreatedAt(Instant.now());
        upload = uploadRepository.save(upload);

        var createReq = new VersionDtos.CreateVersionRequest(upload.getPublicId(), "ZIP", "初始版本上线", null);

        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/versions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.versionNo").value(1))
            .andExpect(jsonPath("$.data.status").value("PUBLISHING"));

        mockMvc.perform(get("/api/v1/prototypes/" + prototype.getPublicId() + "/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].versionNo").value(1))
            .andExpect(jsonPath("$.data[0].changeLog").value("初始版本上线"))
            .andExpect(jsonPath("$.data[0].isCurrent").value(false));
    }

    @Test
    @WithMockUser(username = "creator_user", roles = {"CREATOR"})
    void switchCurrentAndDownloadTicketFlow() throws Exception {
        PrototypeVersionEntity v1 = new PrototypeVersionEntity();
        v1.setPublicId(idGenerator.nextId());
        v1.setPrototype(prototype);
        v1.setVersionNo(1);
        v1.setChangeLog("v1");
        v1.setStatus(VersionStatus.PUBLISHED);
        v1.setSourceType("ZIP");
        v1.setSourceObjectKey("source/v1.zip");
        v1.setSourceSize(100L);
        v1.setChecksum("hash");
        v1.setCreatedBy(creatorUser);
        v1 = versionRepository.save(v1);

        // Switch current version
        var switchReq = new VersionDtos.SwitchCurrentVersionRequest(null, "设为初始生产版本");
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/versions/" + v1.getPublicId() + "/switch")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(switchReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isCurrent").value(true));

        // Create download ticket
        String ticketResStr = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/versions/" + v1.getPublicId() + "/download-ticket")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.url").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String downloadUrl = objectMapper.readTree(ticketResStr).path("data").path("url").asText();

        // Download file via download ticket (anonymous permitAll)
        mockMvc.perform(get(downloadUrl))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/octet-stream"))
            .andExpect(header().exists("Content-Disposition"));
    }
}
