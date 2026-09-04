package com.company.prototype.api.prototype;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.TagEntity;
import com.company.prototype.persistence.prototype.TagRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class PrototypeControllerIT {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PrototypeRepository prototypeRepository;

    @Autowired
    private com.company.prototype.common.id.PublicIdGenerator publicIdGenerator;

    private UserEntity adminUser;
    private UserEntity creatorUser;
    private UserEntity otherCreatorUser;
    private UserEntity viewerUser;
    private CategoryEntity category;
    private TagEntity tag;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();

        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        tagRepository.deleteAll();

        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "管理员")));
        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创建者")));
        RoleEntity viewerRole = roleRepository.findByCode("VIEWER")
            .orElseGet(() -> roleRepository.save(new RoleEntity("VIEWER", "查看者")));

        adminUser = new UserEntity();
        adminUser.setPublicId(publicIdGenerator.nextId());
        adminUser.setUsername("admin");
        adminUser.setPasswordHash("hashed");
        adminUser.setDisplayName("系统管理员");
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);

        creatorUser = new UserEntity();
        creatorUser.setPublicId(publicIdGenerator.nextId());
        creatorUser.setUsername("creator");
        creatorUser.setPasswordHash("hashed");
        creatorUser.setDisplayName("主设计人员");
        creatorUser.setRoles(Set.of(creatorRole));
        creatorUser = userRepository.save(creatorUser);

        otherCreatorUser = new UserEntity();
        otherCreatorUser.setPublicId(publicIdGenerator.nextId());
        otherCreatorUser.setUsername("other");
        otherCreatorUser.setPasswordHash("hashed");
        otherCreatorUser.setDisplayName("其他设计人员");
        otherCreatorUser.setRoles(Set.of(creatorRole));
        otherCreatorUser = userRepository.save(otherCreatorUser);

        viewerUser = new UserEntity();
        viewerUser.setPublicId(publicIdGenerator.nextId());
        viewerUser.setUsername("viewer");
        viewerUser.setPasswordHash("hashed");
        viewerUser.setDisplayName("普通查看者");
        viewerUser.setRoles(Set.of(viewerRole));
        viewerUser = userRepository.save(viewerUser);

        category = new CategoryEntity();
        category.setCode("finance");
        category.setName("金融业务");
        category = categoryRepository.save(category);

        tag = new TagEntity();
        tag.setName("React前端");
        tag.setColor("#1677ff");
        tag = tagRepository.save(tag);
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void viewerCannotCreatePrototype() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload("test-proto", "原型名称", creatorUser.getPublicId())))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCannotEditUnrelatedPrototype() throws Exception {
        // Prototype owned by otherCreatorUser
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(publicIdGenerator.nextId());
        proto.setCode("other-proto");
        proto.setName("其他原型");
        proto.setCategory(category);
        proto.setCreatedBy(otherCreatorUser);
        proto.setOwner(otherCreatorUser);
        proto.setVisibility("ALL_INTERNAL");
        proto = prototypeRepository.save(proto);

        mockMvc.perform(put("/api/v1/prototypes/" + proto.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"" + proto.getRowVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload("更新名称", otherCreatorUser.getPublicId())))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCanCreateAndEditOwnPrototypeWithOptimisticLock() throws Exception {
        // 1. Create prototype
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload("my-proto", "我的原型", creatorUser.getPublicId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.code").value("my-proto"))
            .andExpect(jsonPath("$.data.reviewStatus").value("DRAFT"));

        PrototypeEntity created = prototypeRepository.findByCodeAndDeletedAtIsNull("my-proto").orElseThrow();

        // 2. Edit with wrong If-Match returns 409
        mockMvc.perform(put("/api/v1/prototypes/" + created.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"999\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload("修改原型名称", creatorUser.getPublicId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));

        // 3. Edit with correct If-Match succeeds
        mockMvc.perform(put("/api/v1/prototypes/" + created.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"" + created.getRowVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload("修改原型名称", creatorUser.getPublicId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("修改原型名称"));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCanAssignMultipleOwnersWhenCreating() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayloadWithOwners("team-owned", "协作原型", creatorUser.getPublicId(), otherCreatorUser.getPublicId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.createdBy.publicId").value(creatorUser.getPublicId()))
            .andExpect(jsonPath("$.data.owners[*].publicId", hasItems(creatorUser.getPublicId(), otherCreatorUser.getPublicId())));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCreateWithoutOwnerIdDefaultsToSelf() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayloadWithoutOwner("no-owner", "无指定负责人")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.owner.publicId").value(creatorUser.getPublicId()))
            .andExpect(jsonPath("$.data.owners[*].publicId", hasItem(creatorUser.getPublicId())));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCanAssignOwnerWhenCreating() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload("admin-assign", "管理员指定", creatorUser.getPublicId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.createdBy.publicId").value(adminUser.getPublicId()))
            .andExpect(jsonPath("$.data.owner.publicId").value(creatorUser.getPublicId()))
            .andExpect(jsonPath("$.data.owners[*].publicId", hasItem(creatorUser.getPublicId())));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void adminCreateWithoutOwnerIdDefaultsToSelf() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayloadWithoutOwner("admin-no-owner", "管理员未指定负责人")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.owner.publicId").value(adminUser.getPublicId()));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCanAddCoOwnersOnUpdate() throws Exception {
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(publicIdGenerator.nextId());
        proto.setCode("keep-owner");
        proto.setName("可增加协作人");
        proto.setCategory(category);
        proto.setCreatedBy(creatorUser);
        proto.setOwner(creatorUser);
        proto.setOwners(Set.of(creatorUser));
        proto.setVisibility("ALL_INTERNAL");
        proto = prototypeRepository.save(proto);

        mockMvc.perform(put("/api/v1/prototypes/" + proto.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"" + proto.getRowVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayloadWithOwners("增加协作人", creatorUser.getPublicId(), otherCreatorUser.getPublicId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("增加协作人"))
            .andExpect(jsonPath("$.data.owners[*].publicId", hasItems(creatorUser.getPublicId(), otherCreatorUser.getPublicId())));
    }

    @Test
    @WithMockUser(username = "other", roles = {"CREATOR"})
    void assignedCoOwnerCanEditPrototype() throws Exception {
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(publicIdGenerator.nextId());
        proto.setCode("co-owned");
        proto.setName("协作编辑");
        proto.setCategory(category);
        proto.setCreatedBy(creatorUser);
        proto.setOwner(creatorUser);
        proto.setOwners(Set.of(otherCreatorUser));
        proto.setVisibility("ALL_INTERNAL");
        proto = prototypeRepository.save(proto);

        mockMvc.perform(put("/api/v1/prototypes/" + proto.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"" + proto.getRowVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload("协作人修改名称", otherCreatorUser.getPublicId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("协作人修改名称"));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void viewerCannotBeAssignedAsOwner() throws Exception {
        mockMvc.perform(post("/api/v1/prototypes")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload("bad-owner", "错误负责人", viewerUser.getPublicId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void creatorCanListAssignableOwners() throws Exception {
        mockMvc.perform(get("/api/v1/users/assignable-owners"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].username", hasItems("admin", "creator", "other")))
            .andExpect(jsonPath("$.data[*].username", not(hasItem("viewer"))));
    }

    @Test
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void viewerCannotListAssignableOwners() throws Exception {
        mockMvc.perform(get("/api/v1/users/assignable-owners"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "creator", roles = {"CREATOR"})
    void archivedPrototypeCannotBeEdited() throws Exception {
        PrototypeEntity proto = new PrototypeEntity();
        proto.setPublicId(publicIdGenerator.nextId());
        proto.setCode("archived-proto");
        proto.setName("已归档原型");
        proto.setCategory(category);
        proto.setCreatedBy(creatorUser);
        proto.setOwner(creatorUser);
        proto.setArchived(true);
        proto = prototypeRepository.save(proto);

        mockMvc.perform(put("/api/v1/prototypes/" + proto.getPublicId())
                .with(csrf())
                .header(HttpHeaders.IF_MATCH, "\"" + proto.getRowVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload("尝试修改归档原型", creatorUser.getPublicId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PROTOTYPE_ARCHIVED"));
    }

    private String createPayload(String code, String name, String ownerId) {
        return """
{
  "code": "%s",
  "name": "%s",
  "description": "测试描述",
  "categoryId": "%s",
  "ownerId": "%s",
  "visibility": "ALL_INTERNAL",
  "tagIds": ["%s"]
}
""".formatted(code, name, category.getCode(), ownerId, tag.getName());
    }

    private String createPayloadWithoutOwner(String code, String name) {
        return """
{
  "code": "%s",
  "name": "%s",
  "description": "测试描述",
  "categoryId": "%s",
  "visibility": "ALL_INTERNAL",
  "tagIds": ["%s"]
}
""".formatted(code, name, category.getCode(), tag.getName());
    }

    private String createPayloadWithOwners(String code, String name, String... ownerIds) {
        String ownersJson = java.util.Arrays.stream(ownerIds)
            .map(id -> "\"" + id + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
        return """
{
  "code": "%s",
  "name": "%s",
  "description": "测试描述",
  "categoryId": "%s",
  "ownerIds": [%s],
  "visibility": "ALL_INTERNAL",
  "tagIds": ["%s"]
}
""".formatted(code, name, category.getCode(), ownersJson, tag.getName());
    }

    private String updatePayloadWithOwners(String name, String... ownerIds) {
        String ownersJson = java.util.Arrays.stream(ownerIds)
            .map(id -> "\"" + id + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
        return """
{
  "name": "%s",
  "description": "更新后的描述",
  "categoryId": "%s",
  "ownerIds": [%s],
  "visibility": "ALL_INTERNAL",
  "tagIds": ["%s"]
}
""".formatted(name, category.getCode(), ownersJson, tag.getName());
    }

    private String updatePayload(String name, String ownerId) {
        return """
{
  "name": "%s",
  "description": "更新后的描述",
  "categoryId": "%s",
  "ownerId": "%s",
  "visibility": "ALL_INTERNAL",
  "tagIds": ["%s"]
}
""".formatted(name, category.getCode(), ownerId, tag.getName());
    }
}
