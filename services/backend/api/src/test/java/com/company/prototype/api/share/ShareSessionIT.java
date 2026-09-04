package com.company.prototype.api.share;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.id.UlidPublicIdGenerator;
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
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class ShareSessionIT {

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
    private PrototypeShareLinkRepository shareLinkRepository;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UlidPublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    private UserEntity owner;
    private PrototypeEntity prototype;
    private PrototypeVersionEntity version;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        Set<String> keys = redisTemplate.keys("share:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        shareLinkRepository.deleteAll();
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

        owner = new UserEntity();
        owner.setPublicId(idGenerator.nextId());
        owner.setUsername("share_owner");
        owner.setPasswordHash("hash");
        owner.setDisplayName("分享所有者");
        owner.setRoles(Set.of(creatorRole));
        owner = userRepository.save(owner);

        category = new CategoryEntity();
        category.setCode("share-cat");
        category.setName("分享分类");
        category = categoryRepository.save(category);

        prototype = new PrototypeEntity();
        prototype.setPublicId(idGenerator.nextId());
        prototype.setCode("share-proto");
        prototype.setName("对外演示原型");
        prototype.setCategory(category);
        prototype.setCreatedBy(owner);
        prototype.setOwner(owner);
        prototype = prototypeRepository.save(prototype);

        version = new PrototypeVersionEntity();
        version.setPublicId(idGenerator.nextId());
        version.setPrototype(prototype);
        version.setVersionNo(1);
        version.setStatus(com.company.prototype.persistence.version.VersionStatus.PUBLISHED);
        version.setSourceType("ZIP");
        version.setSourceObjectKey("prototypes/" + prototype.getId() + "/v1/source.zip");
        version.setSourceSize(1024L);
        version.setChecksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        version.setPublishPrefix("prototypes/" + prototype.getId() + "/v1/");
        version.setEntryPath("index.html");
        version.setChangeLog("初始版本");
        version.setCreatedBy(owner);
        version = versionRepository.save(version);

        prototype.setCurrentVersionId(version.getId());
        prototype = prototypeRepository.save(prototype);
    }

    @Test
    @WithMockUser(username = "share_owner", roles = {"CREATOR"})
    void repeatedIdempotencyKeyDoesNotRevealRawTokenAgain() throws Exception {
        var createReq = new ShareDtos.CreateShareRequest("公开评审链接", null, null, "ALL", true, true);

        var firstRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares")
                .with(csrf())
                .header("Idempotency-Key", "idemp-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rawUrl").isNotEmpty())
            .andExpect(jsonPath("$.data.secretAvailable").value(true))
            .andReturn();

        var firstData = objectMapper.readTree(firstRes.getResponse().getContentAsString()).get("data");
        String firstUrl = firstData.get("rawUrl").asText();
        String shareId = firstData.get("shareId").asText();

        var secondRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares")
                .with(csrf())
                .header("Idempotency-Key", "idemp-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.shareId").value(shareId))
            .andExpect(jsonPath("$.data.rawUrl").doesNotExist())
            .andExpect(jsonPath("$.data.secretAvailable").value(false))
            .andReturn();

        PrototypeShareLinkEntity entity = shareLinkRepository.findByPublicId(shareId).orElseThrow();
        assertThat(entity.getTokenDigest()).doesNotContain(firstUrl);
    }

    @Test
    @WithMockUser(username = "share_owner", roles = {"CREATOR"})
    void passwordResetAndDisableInvalidateSessionAndContentTicket() throws Exception {
        var createReq = new ShareDtos.CreateShareRequest("密码保护链接", "initialPass123", null, "ALL", true, true);

        var createRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andReturn();

        var data = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data");
        String rawUrl = data.get("rawUrl").asText();
        String shareId = data.get("shareId").asText();
        String rawToken = rawUrl.substring(rawUrl.lastIndexOf('/') + 1);

        // 1. Bootstrap without session returns requiresPassword: true
        mockMvc.perform(get("/share-api/v1/shares/" + rawToken + "/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requiresPassword").value(true))
            .andExpect(jsonPath("$.data.authenticated").value(false));

        // 2. Verify password succeeds and sets session cookie
        var verifyRes = mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/verify-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShareDtos.VerifyPasswordRequest("initialPass123", "外部测试"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true))
            .andReturn();

        Cookie sessionCookie = verifyRes.getResponse().getCookie("PH_SHARE_SESSION");
        assertThat(sessionCookie).isNotNull();

        // 3. Issue content ticket with session succeeds
        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/content-ticket")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ticket").isNotEmpty());

        // 4. Reset password increments authEpoch and invalidates session
        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares/" + shareId + "/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShareDtos.ResetPasswordRequest("newPass456"))))
            .andExpect(status().isOk());

        // 5. Old session is now rejected
        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/content-ticket")
                .cookie(sessionCookie))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "share_owner", roles = {"CREATOR"})
    void passwordlessBootstrapSetsSessionCookieAndDisabledShareCannotIssueTicket() throws Exception {
        var createReq = new ShareDtos.CreateShareRequest("无密码链接", null, null, "NONE", true, true);
        var createRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isOk())
            .andReturn();

        var data = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data");
        String rawUrl = data.get("rawUrl").asText();
        String shareId = data.get("shareId").asText();
        String rawToken = rawUrl.substring(rawUrl.lastIndexOf('/') + 1);

        var bootstrapRes = mockMvc.perform(get("/share-api/v1/shares/" + rawToken + "/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.authenticated").value(true))
            .andExpect(jsonPath("$.data.csrfToken").isNotEmpty())
            .andExpect(jsonPath("$.data.currentVersionPublicId").value(version.getPublicId()))
            .andExpect(jsonPath("$.data.currentVersionNo").value(1))
            .andReturn();
        Cookie sessionCookie = bootstrapRes.getResponse().getCookie("PH_SHARE_SESSION");
        assertThat(sessionCookie).isNotNull();
        String csrfToken = objectMapper.readTree(bootstrapRes.getResponse().getContentAsString())
            .get("data").get("csrfToken").asText();

        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/comments")
                .cookie(sessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new com.company.prototype.api.comment.CommentDtos.CreateCommentRequest(version.getPublicId(), null, "访客评论")
                )))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/comments")
                .cookie(sessionCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("Idempotency-Key", "guest-fallback-public")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new com.company.prototype.api.comment.CommentDtos.CreateCommentRequest("public", null, "访客评论-缺版本")
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.versionPublicId").value(version.getPublicId()));

        mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares/" + shareId + "/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShareDtos.UpdateStatusRequest("DISABLED"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/content-ticket")
                .cookie(sessionCookie))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/share-api/v1/shares/" + rawToken + "/comments")
                .cookie(sessionCookie))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "share_owner", roles = {"CREATOR"})
    void expiredShareCannotIssueContentTicket() throws Exception {
        var createRes = mockMvc.perform(post("/api/v1/prototypes/" + prototype.getPublicId() + "/shares")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"过期链接","specScope":"NONE","allowComment":false,"allowPublicAttachment":false,"expiresAt":"2020-01-01T00:00:00Z"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String rawUrl = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data").get("rawUrl").asText();
        String rawToken = rawUrl.substring(rawUrl.lastIndexOf('/') + 1);

        mockMvc.perform(post("/share-api/v1/shares/" + rawToken + "/content-ticket"))
            .andExpect(status().isForbidden());
    }
}
