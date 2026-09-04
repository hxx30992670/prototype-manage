package com.company.prototype.api.comment;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.persistence.comment.PrototypeCommentRepository;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class CommentControllerIT {

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
    private PrototypeVersionRepository versionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PrototypeCommentRepository commentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    private UserEntity owner;
    private UserEntity viewer;
    private PrototypeEntity prototype;
    private PrototypeVersionEntity version;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        Set<String> keys = redisTemplate.keys("comment:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        commentRepository.deleteAll();
        prototypeRepository.findAll().forEach(p -> {
            p.setCurrentVersionId(null);
            prototypeRepository.save(p);
        });
        prototypeRepository.flush();
        versionRepository.deleteAll();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创作者")));
        RoleEntity viewerRole = roleRepository.findByCode("VIEWER")
            .orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "查看者")));

        owner = new UserEntity();
        owner.setPublicId(idGenerator.nextId());
        owner.setUsername("comment_owner");
        owner.setPasswordHash("hash");
        owner.setDisplayName("需求负责人");
        owner.setRoles(Set.of(creatorRole));
        owner = userRepository.save(owner);

        viewer = new UserEntity();
        viewer.setPublicId(idGenerator.nextId());
        viewer.setUsername("comment_viewer");
        viewer.setPasswordHash("hash");
        viewer.setDisplayName("评审人员");
        viewer.setRoles(Set.of(viewerRole));
        viewer = userRepository.save(viewer);

        category = new CategoryEntity();
        category.setCode("comment-cat");
        category.setName("评论分类");
        category = categoryRepository.save(category);

        prototype = new PrototypeEntity();
        prototype.setPublicId(idGenerator.nextId());
        prototype.setCode("comment-proto");
        prototype.setName("带评审的原型");
        prototype.setCategory(category);
        prototype.setCreatedBy(owner);
        prototype.setOwner(owner);
        prototype = prototypeRepository.save(prototype);

        version = new PrototypeVersionEntity();
        version.setPublicId(idGenerator.nextId());
        version.setPrototype(prototype);
        version.setVersionNo(1);
        version.setStatus(VersionStatus.PUBLISHED);
        version.setSourceType("ZIP");
        version.setSourceObjectKey("prototypes/" + prototype.getId() + "/v1/source.zip");
        version.setSourceSize(1024L);
        version.setChecksum("checksum-123");
        version.setPublishPrefix("prototypes/" + prototype.getId() + "/v1/");
        version.setEntryPath("index.html");
        version.setChangeLog("初始版本");
        version.setCreatedBy(owner);
        version = versionRepository.save(version);

        prototype.setCurrentVersionId(version.getId());
        prototype = prototypeRepository.save(prototype);
    }

    @Test
    @WithMockUser(username = "comment_viewer", roles = {"VIEWER"})
    void commentKeepsViewedVersionAndOnlyCreatorOwnerOrAdminCanResolve() throws Exception {
        var createReq = new CommentDtos.CreateCommentRequest(version.getPublicId(), null, "页面按钮无响应反馈");

        var createRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.versionPublicId").value(version.getPublicId()))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andReturn();

        var commentData = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data");
        String commentId = commentData.get("publicId").asText();
        long rowVersion = commentData.get("rowVersion").asLong();

        // 1. Viewer cannot resolve
        var resolveReq = new CommentDtos.ResolveCommentRequest("RESOLVED", "已在v2修复", rowVersion);
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments/" + commentId + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resolveReq)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "comment_owner", roles = {"CREATOR"})
    void ownerCanResolveComment() throws Exception {
        var createReq = new CommentDtos.CreateCommentRequest(version.getPublicId(), null, "待处理评审建议");

        var createRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

        var commentData = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data");
        String commentId = commentData.get("publicId").asText();
        long rowVersion = commentData.get("rowVersion").asLong();

        var resolveReq = new CommentDtos.ResolveCommentRequest("RESOLVED", "已确认采纳", rowVersion);
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments/" + commentId + "/resolve")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resolveReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RESOLVED"))
            .andExpect(jsonPath("$.data.resolveNote").value("已确认采纳"));
    }

    @Test
    @WithMockUser(username = "comment_viewer", roles = {"VIEWER"})
    void commentPostRequiresIdempotencyKeyAndDetectsReuse() throws Exception {
        var req1 = new CommentDtos.CreateCommentRequest(version.getPublicId(), null, "幂等测试内容");

        // First call creates comment
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .header("Idempotency-Key", "idemp-comment-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content").value("幂等测试内容"));

        // Repeated call with same idempotency key and same content returns existing comment
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .header("Idempotency-Key", "idemp-comment-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.content").value("幂等测试内容"));

        // Call with same idempotency key but different content fails with 409
        var req2 = new CommentDtos.CreateCommentRequest(version.getPublicId(), null, "完全不同的新内容");
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .header("Idempotency-Key", "idemp-comment-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "comment_owner", roles = {"CREATOR"})
    void replyCannotAttachToCommentFromAnotherPrototype() throws Exception {
        PrototypeEntity other = new PrototypeEntity();
        other.setPublicId(idGenerator.nextId());
        other.setCode("comment-other");
        other.setName("另一个原型");
        other.setCategory(category);
        other.setCreatedBy(owner);
        other.setOwner(owner);
        other = prototypeRepository.save(other);

        PrototypeVersionEntity otherVersion = new PrototypeVersionEntity();
        otherVersion.setPublicId(idGenerator.nextId());
        otherVersion.setPrototype(other);
        otherVersion.setVersionNo(1);
        otherVersion.setStatus(VersionStatus.PUBLISHED);
        otherVersion.setSourceType("ZIP");
        otherVersion.setSourceObjectKey("prototypes/" + other.getId() + "/v1/source.zip");
        otherVersion.setSourceSize(1024L);
        otherVersion.setChecksum("checksum-other");
        otherVersion.setChangeLog("other");
        otherVersion.setCreatedBy(owner);
        otherVersion = versionRepository.save(otherVersion);

        var otherCommentReq = new CommentDtos.CreateCommentRequest(otherVersion.getPublicId(), null, "其他原型评论");
        var otherRes = mockMvc.perform(post("/api/v1/prototypes/" + other.getPublicId() + "/comments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(otherCommentReq)))
            .andExpect(status().isCreated())
            .andReturn();
        String otherCommentId = objectMapper.readTree(otherRes.getResponse().getContentAsString()).get("data").get("publicId").asText();

        var crossReply = new CommentDtos.CreateCommentRequest(version.getPublicId(), otherCommentId, "跨原型回复");
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/comments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(crossReply)))
            .andExpect(status().isBadRequest());
    }
}
