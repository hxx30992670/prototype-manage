package com.company.prototype.api.spec;

import com.company.prototype.api.PrototypeApiApplication;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.persistence.prototype.CategoryEntity;
import com.company.prototype.persistence.prototype.CategoryRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.spec.PrototypeSpecRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PrototypeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class PrototypeSpecControllerIT {

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
    private PrototypeSpecRepository specRepository;

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

        specRepository.deleteAll();
        prototypeRepository.findAll().forEach(p -> {
            p.setCurrentVersionId(null);
            prototypeRepository.save(p);
        });
        prototypeRepository.flush();
        prototypeRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();

        RoleEntity creatorRole = roleRepository.findByCode("CREATOR")
            .orElseGet(() -> roleRepository.save(new RoleEntity("CREATOR", "创作者")));

        creatorUser = new UserEntity();
        creatorUser.setPublicId(idGenerator.nextId());
        creatorUser.setUsername("spec_creator");
        creatorUser.setPasswordHash("hash");
        creatorUser.setDisplayName("需求设计");
        creatorUser.setRoles(Set.of(creatorRole));
        creatorUser = userRepository.save(creatorUser);

        category = new CategoryEntity();
        category.setCode("oa");
        category.setName("协同办公");
        category = categoryRepository.save(category);

        prototype = new PrototypeEntity();
        prototype.setPublicId(idGenerator.nextId());
        prototype.setCode("oa-spec");
        prototype.setName("协同系统");
        prototype.setCategory(category);
        prototype.setCreatedBy(creatorUser);
        prototype.setOwner(creatorUser);
        prototype = prototypeRepository.save(prototype);
    }

    @Test
    @WithMockUser(username = "spec_creator", roles = {"CREATOR"})
    void staleSpecUpdateReturnsConflictAndDoesNotOverwrite() throws Exception {
        var firstUpdate = new SpecDtos.UpdateSpecRequest("目标1", "流程1", "规则1", "约束1", "数据1", "验收1", "# 补充说明", 0L);

        mockMvc.perform(put("/api/v1/prototypes/" + prototype.getPublicId() + "/spec")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goal").value("目标1"))
            .andExpect(jsonPath("$.data.rowVersion").value(0));

        // Stale update using 99 instead of current rowVersion
        var staleUpdate = new SpecDtos.UpdateSpecRequest("过期覆盖", "流程", "规则", "约束", "数据", "验收", "# 补充说明", 99L);
        mockMvc.perform(put("/api/v1/prototypes/" + prototype.getPublicId() + "/spec")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(staleUpdate)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ApiErrorCode.RESOURCE_VERSION_CONFLICT.name()));
    }

    @Test
    @WithMockUser(username = "spec_creator", roles = {"CREATOR"})
    void archivedPrototypeRejectsSpecUpdate() throws Exception {
        prototype.setArchived(true);
        prototypeRepository.save(prototype);

        var updateReq = new SpecDtos.UpdateSpecRequest("目标", "流程", "规则", "约束", "数据", "验收", "# 说明", 0L);
        mockMvc.perform(put("/api/v1/prototypes/" + prototype.getPublicId() + "/spec")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(ApiErrorCode.PROTOTYPE_ARCHIVED.name()));
    }

    @Test
    @WithMockUser(username = "spec_creator", roles = {"CREATOR"})
    void xssMarkdownPayloadIsRejected() throws Exception {
        var xssReq = new SpecDtos.UpdateSpecRequest("目标", "流程", "规则", "约束", "数据", "验收", "<script>alert(1)</script>", 0L);
        mockMvc.perform(put("/api/v1/prototypes/" + prototype.getPublicId() + "/spec")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(xssReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(ApiErrorCode.BAD_REQUEST.name()));
    }
}
