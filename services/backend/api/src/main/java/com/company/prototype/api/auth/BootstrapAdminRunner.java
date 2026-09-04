package com.company.prototype.api.auth;

import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("!test")
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PublicIdGenerator idGenerator = new UlidPublicIdGenerator();

    @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:Admin@123456}")
    private String adminPassword;

    public BootstrapAdminRunner(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            log.info("Admin user '{}' already exists, skip bootstrap", adminUsername);
            return;
        }

        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseGet(() -> roleRepository.save(new RoleEntity("ADMIN", "系统管理员")));

        UserEntity admin = new UserEntity();
        admin.setPublicId(idGenerator.nextId());
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("系统管理员");
        admin.setStatus("ACTIVE");
        admin.setMustChangePassword(true);
        admin.setRoles(Set.of(adminRole));

        userRepository.save(admin);
        log.info("Bootstrap admin user '{}' created successfully", adminUsername);
    }
}
