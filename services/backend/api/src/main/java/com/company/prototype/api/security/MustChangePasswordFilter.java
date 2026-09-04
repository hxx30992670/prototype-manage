package com.company.prototype.api.security;

import com.company.prototype.common.error.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * mustChangePassword=true 时，除登录、改密、退出、当前用户与 CSRF 外拒绝全部业务 API。
 * 登录必须放行，否则强制改密会话会挡住后续账号（含管理员）重新登录。
 */
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
        "GET /api/v1/auth/me",
        "GET /api/v1/auth/csrf",
        "GET /api/v1/auth/captcha",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/change-password",
        "POST /api/v1/auth/logout"
    );

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser user && user.mustChangePassword()) {
            String path = request.getRequestURI();
            if (path != null && path.startsWith("/api/v1/") && !isAllowed(request.getMethod(), path)) {
                response.setStatus(ApiErrorCode.PASSWORD_CHANGE_REQUIRED.getHttpStatus());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(response.getWriter(), Map.of(
                    "code", ApiErrorCode.PASSWORD_CHANGE_REQUIRED.name(),
                    "message", ApiErrorCode.PASSWORD_CHANGE_REQUIRED.getDefaultMessage()
                ));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String method, String path) {
        return ALLOWED.contains(method.toUpperCase() + " " + path);
    }
}
