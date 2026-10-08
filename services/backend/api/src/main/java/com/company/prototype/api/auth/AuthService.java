package com.company.prototype.api.auth;

import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.user.RoleEntity;
import com.company.prototype.persistence.user.UserEntity;
import com.company.prototype.persistence.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final AdminConfigService configService;
    private final com.company.prototype.api.monitoring.PlatformMetrics platformMetrics;

    @Value("${server.servlet.session.cookie.name:PH_ADMIN_SESSION}")
    private String cookieName = "PH_ADMIN_SESSION";

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean cookieSecure = false;

    @Value("${server.servlet.session.timeout:10d}")
    private String sessionTimeout = "10d";

    // In-memory fallback if Redis is not configured
    private final Map<String, Integer> memoryFailCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> memoryLockExpiry = new ConcurrentHashMap<>();

    public AuthService(
        UserRepository userRepository,
        AuditLogRepository auditLogRepository,
        PasswordEncoder passwordEncoder,
        @Autowired(required = false) StringRedisTemplate redisTemplate,
        @Autowired(required = false) AdminConfigService configService,
        @Autowired(required = false) com.company.prototype.api.monitoring.PlatformMetrics platformMetrics
    ) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.configService = configService;
        this.platformMetrics = platformMetrics;
    }

    @Transactional
    public AuthDtos.UserProfileResponse login(AuthDtos.LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String username = req.username().trim();

        validateCaptchaIfEnabled(req);

        // 1. Check rate limit
        if (isRateLimited(username, clientIp)) {
            countLoginFailure();
            logAudit("LOGIN_FAILURE", "UNKNOWN", null, "FAILURE", clientIp, request.getHeader("User-Agent"), "账户或IP由于失败次数过多被锁定");
            throw new ApiException(ApiErrorCode.AUTH_RATE_LIMITED);
        }

        // 2. Find user
        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash())) {
            countLoginFailure();
            recordLoginFailure(username, clientIp);
            logAudit("LOGIN_FAILURE", "USER", username, "FAILURE", clientIp, request.getHeader("User-Agent"), "用户名或密码错误");
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        UserEntity user = userOpt.get();

        // 3. Check status
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            countLoginFailure();
            logAudit("LOGIN_FAILURE", "USER", user.getPublicId(), "FAILURE", clientIp, request.getHeader("User-Agent"), "账号已被禁用");
            throw new ApiException(ApiErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        // 4. Clear rate limit on success
        clearRateLimit(username, clientIp);

        // 5. Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // 6. Rotate session
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        Duration loginLifetime = DurationStyle.detectAndParse(sessionTimeout, ChronoUnit.SECONDS);
        newSession.setMaxInactiveInterval(Math.toIntExact(loginLifetime.getSeconds()));

        Set<String> roleCodes = user.getRoles().stream()
            .map(RoleEntity::getCode)
            .collect(Collectors.toSet());

        List<SimpleGrantedAuthority> authorities = roleCodes.stream()
            .map(r -> r.startsWith("ROLE_") ? new SimpleGrantedAuthority(r) : new SimpleGrantedAuthority("ROLE_" + r))
            .collect(Collectors.toList());

        CurrentUser currentUser = new CurrentUser(
            user.getId(),
            user.getPublicId(),
            user.getUsername(),
            user.getDisplayName(),
            roleCodes,
            user.isMustChangePassword()
        );

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(currentUser, null, authorities);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        newSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        // Explicitly write Host-only HttpOnly Root-path session cookie
        if (response != null) {
            ResponseCookie sessionCookie = ResponseCookie.from(cookieName, newSession.getId())
                .path("/")
                .maxAge(loginLifetime)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(cookieSecure)
                .build();
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
        }

        // 7. Audit log
        logAudit("LOGIN_SUCCESS", "USER", user.getPublicId(), "SUCCESS", clientIp, request.getHeader("User-Agent"), "登录成功");

        return new AuthDtos.UserProfileResponse(
            user.getPublicId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getDepartment(),
            roleCodes,
            user.isMustChangePassword()
        );
    }

    public void logout(CurrentUser currentUser, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = getClientIp(request);
        String targetId = currentUser != null ? currentUser.publicId() : "ANONYMOUS";
        Long actorId = currentUser != null ? currentUser.id() : null;

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        if (response != null) {
            ResponseCookie clearCookie = ResponseCookie.from(cookieName, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .secure(cookieSecure)
                .build();
            response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
        }

        logAudit("LOGOUT", "USER", targetId, "SUCCESS", clientIp, request.getHeader("User-Agent"), "用户退出登录");
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, AuthDtos.ChangePasswordRequest req, HttpServletRequest request) {
        UserEntity user = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshSessionMustChangePassword(currentUser, request);

        logAudit("PASSWORD_CHANGE", "USER", user.getPublicId(), "SUCCESS", getClientIp(request), request.getHeader("User-Agent"), "用户修改密码");
    }

    private void refreshSessionMustChangePassword(CurrentUser currentUser, HttpServletRequest request) {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser refreshed = new CurrentUser(
            currentUser.id(),
            currentUser.publicId(),
            currentUser.username(),
            currentUser.displayName(),
            currentUser.roles(),
            false
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            refreshed,
            null,
            existing != null ? existing.getAuthorities() : List.of()
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        }
    }

    public Map<String, Object> createCaptcha() {
        if (!captchaEnabled()) {
            return Map.of("enabled", false);
        }
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        int value = 1000 + new Random().nextInt(9000);
        String code = String.valueOf(value);
        storeCaptcha(captchaId, code);
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="120" height="40">
              <rect width="120" height="40" fill="#f5f5f5"/>
              <text x="18" y="28" font-size="24" font-family="monospace" fill="#333">%s</text>
            </svg>
            """.formatted(code);
        String image = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return Map.of("enabled", true, "captchaId", captchaId, "image", image);
    }

    public AuthDtos.UserProfileResponse getCurrentUserProfile(CurrentUser currentUser) {
        UserEntity user = userRepository.findById(currentUser.id())
            .orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));

        Set<String> roleCodes = user.getRoles().stream()
            .map(RoleEntity::getCode)
            .collect(Collectors.toSet());

        return new AuthDtos.UserProfileResponse(
            user.getPublicId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getDepartment(),
            roleCodes,
            user.isMustChangePassword()
        );
    }

    private boolean isRateLimited(String username, String clientIp) {
        String userKey = "login:fail:user:" + username;
        String ipKey = "login:fail:ip:" + clientIp;

        if (redisTemplate != null) {
            try {
                String userVal = redisTemplate.opsForValue().get(userKey);
                String ipVal = redisTemplate.opsForValue().get(ipKey);
                int userFails = userVal != null ? Integer.parseInt(userVal) : 0;
                int ipFails = ipVal != null ? Integer.parseInt(ipVal) : 0;
                return userFails >= MAX_FAILED_ATTEMPTS || ipFails >= MAX_FAILED_ATTEMPTS;
            } catch (Exception e) {
                log.warn("Redis unavailable for rate limit check, using memory fallback");
            }
        }

        Instant now = Instant.now();
        if (isMemoryLocked(userKey, now) || isMemoryLocked(ipKey, now)) {
            return true;
        }
        return memoryFailCounts.getOrDefault(userKey, 0) >= MAX_FAILED_ATTEMPTS
            || memoryFailCounts.getOrDefault(ipKey, 0) >= MAX_FAILED_ATTEMPTS;
    }

    private boolean isMemoryLocked(String key, Instant now) {
        Instant expiry = memoryLockExpiry.get(key);
        if (expiry != null) {
            if (now.isBefore(expiry)) {
                return true;
            } else {
                memoryLockExpiry.remove(key);
                memoryFailCounts.remove(key);
            }
        }
        return false;
    }

    private void recordLoginFailure(String username, String clientIp) {
        String userKey = "login:fail:user:" + username;
        String ipKey = "login:fail:ip:" + clientIp;

        if (redisTemplate != null) {
            try {
                Long uCount = redisTemplate.opsForValue().increment(userKey);
                if (uCount != null && uCount == 1) {
                    redisTemplate.expire(userKey, LOCK_DURATION);
                }
                Long iCount = redisTemplate.opsForValue().increment(ipKey);
                if (iCount != null && iCount == 1) {
                    redisTemplate.expire(ipKey, LOCK_DURATION);
                }
                return;
            } catch (Exception e) {
                log.warn("Redis unavailable for rate limit increment, using memory fallback");
            }
        }

        int uCount = memoryFailCounts.merge(userKey, 1, Integer::sum);
        int iCount = memoryFailCounts.merge(ipKey, 1, Integer::sum);
        Instant lockUntil = Instant.now().plus(LOCK_DURATION);
        if (uCount >= MAX_FAILED_ATTEMPTS) {
            memoryLockExpiry.put(userKey, lockUntil);
        }
        if (iCount >= MAX_FAILED_ATTEMPTS) {
            memoryLockExpiry.put(ipKey, lockUntil);
        }
    }

    public void resetRateLimits() {
        memoryFailCounts.clear();
        memoryLockExpiry.clear();
        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys("login:fail:*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception ignored) {}
        }
    }

    private void clearRateLimit(String username, String clientIp) {
        String userKey = "login:fail:user:" + username;
        String ipKey = "login:fail:ip:" + clientIp;

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(List.of(userKey, ipKey));
            } catch (Exception ignored) {}
        }
        memoryFailCounts.remove(userKey);
        memoryFailCounts.remove(ipKey);
        memoryLockExpiry.remove(userKey);
        memoryLockExpiry.remove(ipKey);
    }

    private void countLoginFailure() {
        if (platformMetrics != null) {
            try {
                platformMetrics.loginFailure();
            } catch (Exception ignored) {
                // 指标不可用不影响登录流程
            }
        }
    }

    private boolean captchaEnabled() {
        return configService != null && configService.getBoolean("captcha.enabled");
    }

    private void validateCaptchaIfEnabled(AuthDtos.LoginRequest req) {
        if (!captchaEnabled()) {
            return;
        }
        if (req.captchaId() == null || req.captchaId().isBlank() || req.captchaCode() == null || req.captchaCode().isBlank()) {
            throw new ApiException(ApiErrorCode.CAPTCHA_REQUIRED);
        }
        String expected = consumeCaptcha(req.captchaId());
        if (expected == null || !expected.equalsIgnoreCase(req.captchaCode().trim())) {
            throw new ApiException(ApiErrorCode.CAPTCHA_INVALID);
        }
    }

    private void storeCaptcha(String captchaId, String code) {
        String key = "login:captcha:" + captchaId;
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(5));
                return;
            } catch (Exception e) {
                log.warn("Redis unavailable for captcha store, using memory fallback");
            }
        }
        memoryFailCounts.put(key, Integer.parseInt(code));
        memoryLockExpiry.put(key, Instant.now().plus(Duration.ofMinutes(5)));
    }

    private String consumeCaptcha(String captchaId) {
        String key = "login:captcha:" + captchaId;
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(key);
                redisTemplate.delete(key);
                return value;
            } catch (Exception e) {
                log.warn("Redis unavailable for captcha consume, using memory fallback");
            }
        }
        Instant expiry = memoryLockExpiry.remove(key);
        Integer stored = memoryFailCounts.remove(key);
        if (expiry == null || Instant.now().isAfter(expiry) || stored == null) {
            return null;
        }
        return String.valueOf(stored);
    }

    private void logAudit(String action, String targetType, String targetId, String result, String ip, String userAgent, String summary) {
        try {
            AuditLogEntity audit = new AuditLogEntity();
            audit.setTraceId(UUID.randomUUID().toString().replace("-", ""));
            audit.setActorType("USER");
            audit.setAction(action);
            audit.setTargetType(targetType);
            audit.setTargetId(targetId != null ? targetId : "N/A");
            audit.setResult(result);
            audit.setIp(ip);
            audit.setUserAgent(userAgent);
            audit.setSummary(summary);
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to write audit log", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
