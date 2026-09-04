package com.company.prototype.api.user;

import com.company.prototype.api.preview.PreviewTicketService;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.common.id.PublicIdGenerator;
import com.company.prototype.common.id.UlidPublicIdGenerator;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.RoleRepository;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogRepository auditLogRepository;
    private final SessionRegistry sessionRegistry;
    private final PreviewTicketService previewTicketService;
    private final PublicIdGenerator idGenerator = new UlidPublicIdGenerator();
    private final String bootstrapAdminUsername;

    public UserAdminService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        AuditLogRepository auditLogRepository,
        SessionRegistry sessionRegistry,
        PreviewTicketService previewTicketService,
        @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String bootstrapAdminUsername
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
        this.sessionRegistry = sessionRegistry;
        this.previewTicketService = previewTicketService;
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    public Page<UserAdminDtos.UserDetailResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserAdminDtos.UserDetailResponse createUser(UserAdminDtos.CreateUserRequest req) {
        String username = req.username().trim();
        if (isProtectedAdminUsername(username)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "内置管理员账号不可创建或占用");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "用户名已存在");
        }

        Set<RoleEntity> roles = resolveRoles(req.roles());

        UserEntity user = new UserEntity();
        user.setPublicId(idGenerator.nextId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        user.setDepartment(req.department());
        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);
        user.setRoles(roles);

        user = userRepository.save(user);
        logAudit("USER_CREATE", user.getPublicId(), "创建用户: " + user.getUsername());
        return toResponse(user);
    }

    @Transactional
    public UserAdminDtos.UserDetailResponse updateUser(String publicId, UserAdminDtos.UpdateUserRequest req) {
        UserEntity user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(user);

        user.setDisplayName(req.displayName().trim());
        user.setDepartment(req.department());
        if (req.roles() != null) {
            if (req.roles().isEmpty()) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "至少选择一个角色");
            }
            replaceRoles(user, req.roles());
        }
        user = userRepository.save(user);

        logAudit("USER_UPDATE", user.getPublicId(), "更新用户资料" + (req.roles() != null ? "与角色" : ""));
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(String publicId, UserAdminDtos.ResetPasswordRequest req) {
        UserEntity user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(user);

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(true);
        userRepository.save(user);

        logAudit("USER_RESET_PASSWORD", user.getPublicId(), "重置用户密码");
    }

    @Transactional
    public UserAdminDtos.UserDetailResponse updateRoles(String publicId, UserAdminDtos.UpdateRolesRequest req) {
        UserEntity user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(user);

        replaceRoles(user, req.roles());
        user = userRepository.save(user);

        logAudit("USER_UPDATE_ROLES", user.getPublicId(), "更新用户角色");
        return toResponse(user);
    }

    @Transactional
    public UserAdminDtos.UserDetailResponse updateStatus(String publicId, UserAdminDtos.UpdateStatusRequest req) {
        UserEntity user = userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(user);

        String newStatus = req.status().toUpperCase();
        if (!"ACTIVE".equals(newStatus) && !"DISABLED".equals(newStatus)) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "非法的状态值");
        }

        boolean disabling = "DISABLED".equals(newStatus) && !"DISABLED".equalsIgnoreCase(user.getStatus());
        user.setStatus(newStatus);
        user = userRepository.save(user);

        if (disabling) {
            expireAllSessions(user);
            if (previewTicketService != null) {
                previewTicketService.revokeUserTickets(user.getId());
            }
        }

        logAudit("USER_UPDATE_STATUS", user.getPublicId(), "更新用户状态为: " + newStatus);
        return toResponse(user);
    }

    /** 通过 SessionRegistry 强制注销该用户全部已登录会话。 */
    private void expireAllSessions(UserEntity user) {
        if (sessionRegistry == null) {
            return;
        }
        try {
            for (Object principal : sessionRegistry.getAllPrincipals()) {
                if (matchesUser(principal, user)) {
                    sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to expire sessions for user {}: {}", user.getId(), e.getMessage());
        }
    }

    private boolean matchesUser(Object principal, UserEntity user) {
        if (principal instanceof CurrentUser currentUser) {
            return user.getId().equals(currentUser.id())
                || user.getUsername().equals(currentUser.username());
        }
        return principal != null && user.getUsername().equals(principal.toString());
    }

    private void assertMutable(UserEntity user) {
        if (isProtectedAdmin(user)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED, "内置管理员账号不可修改");
        }
    }

    private boolean isProtectedAdmin(UserEntity user) {
        return isProtectedAdminUsername(user.getUsername());
    }

    private boolean isProtectedAdminUsername(String username) {
        return username != null && username.equalsIgnoreCase(bootstrapAdminUsername);
    }

    private void replaceRoles(UserEntity user, Set<String> roleCodes) {
        Set<RoleEntity> resolved = resolveRoles(roleCodes);
        user.getRoles().clear();
        user.getRoles().addAll(resolved);
    }

    private Set<RoleEntity> resolveRoles(Set<String> roleCodes) {
        Set<RoleEntity> roles = new HashSet<>();
        for (String code : roleCodes) {
            String c = code.replace("ROLE_", "");
            RoleEntity role = roleRepository.findByCode(c)
                .orElseGet(() -> roleRepository.save(new RoleEntity(c, c)));
            roles.add(role);
        }
        return roles;
    }

    private UserAdminDtos.UserDetailResponse toResponse(UserEntity user) {
        Set<String> roleCodes = user.getRoles().stream().map(RoleEntity::getCode).collect(Collectors.toSet());
        return new UserAdminDtos.UserDetailResponse(
            user.getPublicId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getDepartment(),
            user.getStatus(),
            roleCodes,
            user.isMustChangePassword(),
            user.getLastLoginAt(),
            user.getCreatedAt(),
            isProtectedAdmin(user)
        );
    }

    private void logAudit(String action, String targetId, String summary) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        audit.setActorType("ADMIN");
        audit.setAction(action);
        audit.setTargetType("USER");
        audit.setTargetId(targetId);
        audit.setResult("SUCCESS");
        audit.setSummary(summary);
        audit.setCreatedAt(Instant.now());
        auditLogRepository.save(audit);
    }
}
