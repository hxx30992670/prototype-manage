package com.company.prototype.api.audit;

import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 统一审计写入入口。
 *
 * 所有审计摘要经过 {@link #sanitize} 脱敏：密码、Token、内容票据、下载票据、
 * 签名查询参数、Cookie 与异常堆栈等敏感内容一律不进入审计记录。
 * 各业务服务继续使用自己的 logAudit 记录业务审计；本服务集中提供脱敏与查询能力，
 * 并作为后续切面审计的公共出口。
 */
@Service
public class AuditService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "(?i)(password|token|ticket|session|signature|credential|secret|csrf|authorization)[=: ]+[^\\s,;&\"']+");
    private static final Pattern BEARER_PATTERN = Pattern.compile(
        "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern RAW_PATTERN = Pattern.compile(
        "(?i)(/s/|/share-api/v1/shares/|/content/c/|/api/v1/downloads/|/share-api/v1/downloads/)[A-Za-z0-9._~-]{8,}");

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 记录一条审计日志。actorId 为 null 时表示匿名/系统操作（如登录失败）。
     * summary 会自动脱敏。
     */
    @Transactional
    public AuditLogEntity record(
        String action,
        String targetType,
        String targetId,
        String result,
        Long actorId,
        String summary
    ) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        audit.setActorType(actorId != null ? "USER" : "SYSTEM");
        audit.setActorId(actorId);
        audit.setAction(action);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId == null ? "-" : targetId);
        audit.setResult(result);
        audit.setSummary(sanitize(summary));
        audit.setCreatedAt(Instant.now());
        return auditLogRepository.save(audit);
    }

    /** 脱敏：替换敏感键值对、Bearer 凭证与 raw Token/票据路径段。 */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String safe = RAW_PATTERN.matcher(text).replaceAll("$1<redacted>");
        safe = BEARER_PATTERN.matcher(safe).replaceAll("Bearer=<redacted>");
        safe = TOKEN_PATTERN.matcher(safe).replaceAll("$1=<redacted>");
        return safe.length() > 1000 ? safe.substring(0, 1000) : safe;
    }
}
