package com.company.prototype.api.security;

import com.company.prototype.common.logging.CredentialRedactor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求级日志上下文：
 * - 生成 traceId 写入 MDC 与响应头（可被调用方传入 X-Trace-Id 沿用）；
 * - 请求 URI 经 {@link CredentialRedactor} 脱敏后写入 MDC，防止 Token/票据进日志；
 * - 已认证用户 publicId 写入 MDC（userPublicId）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank())
            ? CredentialRedactor.redact(incoming)
            : UUID.randomUUID().toString().replace("-", "");
        traceId = traceId.length() > 64 ? traceId.substring(0, 64) : traceId;

        MDC.put("traceId", traceId);
        MDC.put("requestUri", CredentialRedactor.redact(request.getRequestURI()));
        MDC.put("method", request.getMethod());
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            MDC.remove("requestUri");
            MDC.remove("method");
            MDC.remove("userPublicId");
        }
    }

    /** 供认证成功后设置用户上下文（由认证流程调用）。 */
    public static void putUserContext(String userPublicId) {
        if (userPublicId != null && !userPublicId.isBlank()) {
            MDC.put("userPublicId", userPublicId);
        }
    }

    /** 从当前 SecurityContext 同步用户上下文（供过滤器链内使用）。 */
    public static void syncUserContextFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            putUserContext(user.publicId());
        }
    }
}
