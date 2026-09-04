package com.company.prototype.api.audit;

import com.company.prototype.persistence.audit.AuditLogEntity;

import java.time.Instant;

/** 审计日志对外视图：不暴露任何敏感字段。 */
public record AuditLogView(
    Long id,
    String traceId,
    String actorType,
    Long actorId,
    String action,
    String targetType,
    String targetId,
    String result,
    String ip,
    String userAgent,
    String summary,
    Instant createdAt
) {
    public static AuditLogView from(AuditLogEntity e) {
        return new AuditLogView(
            e.getId(),
            e.getTraceId(),
            e.getActorType(),
            e.getActorId(),
            e.getAction(),
            e.getTargetType(),
            e.getTargetId(),
            e.getResult(),
            e.getIp(),
            e.getUserAgent(),
            e.getSummary(),
            e.getCreatedAt()
        );
    }
}
