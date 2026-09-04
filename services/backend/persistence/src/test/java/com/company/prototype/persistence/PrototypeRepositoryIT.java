package com.company.prototype.persistence;

import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.prototype.*;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PrototypeRepositoryIT.TestApplication.class)
@ActiveProfiles("test")
@Testcontainers
class PrototypeRepositoryIT {

    @SpringBootApplication(scanBasePackages = "com.company.prototype.persistence")
    static class TestApplication {}

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

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
    private AuditLogRepository auditLogRepository;

    @Test
    void testEntitiesAndRepositoriesPersistence() {
        // 1. Role
        RoleEntity role = roleRepository.save(new RoleEntity("ROLE_CREATOR", "原型创建者"));
        assertThat(role.getId()).isNotNull();

        // 2. User
        UserEntity user = new UserEntity();
        user.setPublicId("01J8K4X7Q2M6V9T3ABCD000001");
        user.setUsername("developer1");
        user.setPasswordHash("hashed_pwd");
        user.setDisplayName("张三");
        user.setRoles(Set.of(role));
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();
        assertThat(user.getRoles()).hasSize(1);

        // 3. Category & Tag
        CategoryEntity category = categoryRepository.save(new CategoryEntity("CRM", "客户关系管理", 1));
        TagEntity tag = tagRepository.save(new TagEntity("v1.0", "#1890ff"));

        // 4. Prototype
        PrototypeEntity prototype = new PrototypeEntity();
        prototype.setPublicId("01J8K4X7Q2M6V9T3ABCD000002");
        prototype.setCode("CRM-001");
        prototype.setName("客户跟进原型");
        prototype.setCategory(category);
        prototype.setCreatedBy(user);
        prototype.setOwner(user);
        prototype.setTags(Set.of(tag));
        prototype = prototypeRepository.save(prototype);

        assertThat(prototype.getId()).isNotNull();
        assertThat(prototype.getRowVersion()).isEqualTo(0L);

        // 5. Query active prototype
        var found = prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototype.getPublicId());
        assertThat(found).isPresent();

        // 6. Soft delete
        prototype.setDeletedAt(Instant.now());
        prototypeRepository.save(prototype);

        assertThat(prototypeRepository.findByPublicIdAndDeletedAtIsNull(prototype.getPublicId())).isEmpty();
        assertThat(prototypeRepository.findByPublicIdAndDeletedAtIsNotNull(prototype.getPublicId())).isPresent();

        // 7. Audit log
        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId("trace-123");
        audit.setActorType("USER");
        audit.setActorId(user.getId());
        audit.setAction("PROTOTYPE_DELETE");
        audit.setTargetType("PROTOTYPE");
        audit.setTargetId(prototype.getPublicId());
        audit.setResult("SUCCESS");
        audit.setSummary("移入回收站");
        audit = auditLogRepository.save(audit);
        assertThat(audit.getId()).isNotNull();
    }
}
