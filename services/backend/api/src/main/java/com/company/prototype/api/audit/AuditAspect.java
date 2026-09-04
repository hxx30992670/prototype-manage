package com.company.prototype.api.audit;

import com.company.prototype.api.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Long actorId = extractActorId(joinPoint.getArgs());
        String targetId = extractTargetId(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            auditService.record(
                audited.action(),
                audited.targetType(),
                targetId,
                "SUCCESS",
                actorId,
                "调用 " + joinPoint.getSignature().getName()
            );
            return result;
        } catch (Throwable ex) {
            try {
                auditService.record(
                    audited.action(),
                    audited.targetType(),
                    targetId,
                    "FAILURE",
                    actorId,
                    "调用 " + joinPoint.getSignature().getName() + " 失败: " + ex.getMessage()
                );
            } catch (Exception auditError) {
                log.warn("Failed to write failure audit for {}: {}", audited.action(), auditError.getMessage());
            }
            throw ex;
        }
    }

    private Long extractActorId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof CurrentUser user) {
                return user.id();
            }
        }
        return null;
    }

    private String extractTargetId(Object[] args) {
        if (args == null) {
            return "-";
        }
        for (Object arg : args) {
            if (arg instanceof String value && !value.isBlank() && value.length() <= 64) {
                return value;
            }
        }
        return "-";
    }
}
