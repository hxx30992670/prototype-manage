package com.company.prototype.api.attachment;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.attachment.PrototypeAttachmentEntity;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class AttachmentControllerIT {

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
    private PrototypeAttachmentRepository attachmentRepository;

    @Autowired
    private TemporaryUploadRepository uploadRepository;

    @MockitoBean
    private ObjectStorage objectStorage;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    private UserEntity creatorUser;
    private UserEntity viewerUser;
    private PrototypeEntity prototype;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        attachmentRepository.deleteAll();
        prototypeRepository.findAll().forEach(p -> {
            p.setCurrentVersionId(null);
            prototypeRepository.save(p);
        });
        prototypeRepository.flush();
        prototypeRepository.deleteAll();
        uploadRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创作者")));
        RoleEntity viewerRole = roleRepository.findByCode("VIEWER")
            .orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "查看者")));

        creatorUser = new UserEntity();
        creatorUser.setPublicId(idGenerator.nextId());
        creatorUser.setUsername("attach_creator");
        creatorUser.setPasswordHash("hash");
        creatorUser.setDisplayName("设计同学");
        creatorUser.setRoles(Set.of(creatorRole));
        creatorUser = userRepository.save(creatorUser);

        viewerUser = new UserEntity();
        viewerUser.setPublicId(idGenerator.nextId());
        viewerUser.setUsername("attach_viewer");
        viewerUser.setPasswordHash("hash");
        viewerUser.setDisplayName("查看同学");
        viewerUser.setRoles(Set.of(viewerRole));
        viewerUser = userRepository.save(viewerUser);

        category = new CategoryEntity();
        category.setCode("crm");
        category.setName("客户关系");
        category = categoryRepository.save(category);

        prototype = new PrototypeEntity();
        prototype.setPublicId(idGenerator.nextId());
        prototype.setCode("crm-app");
        prototype.setName("CRM 系统");
        prototype.setCategory(category);
        prototype.setCreatedBy(creatorUser);
        prototype.setOwner(creatorUser);
        prototype = prototypeRepository.save(prototype);
    }

    @Test
    @WithMockUser(username = "attach_creator", roles = {"CREATOR"})
    void createAndListAttachmentsFlow() throws Exception {
        TemporaryUploadEntity upload = new TemporaryUploadEntity();
        upload.setPublicId(idGenerator.nextId());
        upload.setUser(creatorUser);
        upload.setFilename("specs.pdf");
        upload.setFileType("ATTACHMENT");
        upload.setClaimedSize(1024L);
        upload.setObjectKey("temp/specs.pdf");
        upload.setStatus("COMPLETED");
        upload.setExpiresAt(Instant.now().plusSeconds(3600));
        upload = uploadRepository.save(upload);

        when(objectStorage.open("temp/specs.pdf"))
            .thenReturn(new ByteArrayInputStream("PDF_DATA".getBytes(StandardCharsets.UTF_8)));

        var createReq = new AttachmentDtos.CreateAttachmentRequest(
            upload.getPublicId(),
            "需求规格书.pdf",
            "DOCUMENT",
            "产品验收标准",
            "INTERNAL",
            null
        );

        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/attachments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("需求规格书.pdf"))
            .andExpect(jsonPath("$.data.accessScope").value("INTERNAL"))
            .andExpect(jsonPath("$.data.mimeType").value("application/pdf"));

        verify(objectStorage).put(
            any(String.class),
            any(InputStream.class),
            eq(1024L),
            eq("application/pdf")
        );

        mockMvc.perform(get("/api/v1/prototypes/" + prototype.getPublicId() + "/attachments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("需求规格书.pdf"));
    }

    @Test
    @WithMockUser(username = "attach_viewer", roles = {"VIEWER"})
    void viewerCanCreateDownloadTicketWithoutAmzSignature() throws Exception {
        PrototypeAttachmentEntity attach = new PrototypeAttachmentEntity();
        attach.setPublicId(idGenerator.nextId());
        attach.setPrototype(prototype);
        attach.setName("icon.png");
        attach.setType("IMAGE");
        attach.setAccessScope("INTERNAL");
        attach.setObjectKey("prototypes/" + prototype.getId() + "/attachments/icon.png");
        attach.setSize(2048L);
        attach.setMimeType("image/png");
        attach.setChecksum("hash");
        attach.setCreatedBy(creatorUser);
        attach.setCreatedAt(Instant.now());
        attach = attachmentRepository.save(attach);

        mockMvc.perform(get("/api/v1/attachments/" + attach.getPublicId() + "/download"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.url").exists())
            .andExpect(jsonPath("$.data.url").value(not(containsString("X-Amz-Signature"))))
            .andExpect(jsonPath("$.data.url").value(containsString("/api/v1/downloads/")));
    }

    @Test
    @WithMockUser(username = "attach_creator", roles = {"CREATOR"})
    void createAttachmentRejectsMismatchedUploadType() throws Exception {
        TemporaryUploadEntity upload = new TemporaryUploadEntity();
        upload.setPublicId(idGenerator.nextId());
        upload.setUser(creatorUser);
        upload.setFilename("cover.png");
        upload.setFileType("COVER");
        upload.setClaimedSize(1024L);
        upload.setObjectKey("temp/cover.png");
        upload.setStatus("COMPLETED");
        upload.setExpiresAt(Instant.now().plusSeconds(3600));
        upload = uploadRepository.save(upload);

        var createReq = new AttachmentDtos.CreateAttachmentRequest(
            upload.getPublicId(),
            "封面误当作附件.png",
            "IMAGE",
            "封面",
            "INTERNAL",
            null
        );

        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/attachments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ApiErrorCode.UPLOAD_TYPE_UNSUPPORTED.name()));
    }
}
