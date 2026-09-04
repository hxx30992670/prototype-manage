package com.company.prototype.api.audit;

import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 审计脱敏测试：任何审计记录不得包含密码、Token、内容票据、下载票据、
 * 签名参数与异常堆栈。本测试验证 AuditService 的脱敏入口，审计记录查询
 * 在 AdminControllerIT 中验证。
 */
class AuditAspectTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService auditService = new AuditService(repository);

    @ParameterizedTest
    @ValueSource(strings = {
        "分享链接: /s/RAW_SHARE_TOKEN",
        "评论接口: /share-api/v1/shares/RAW_SHARE_TOKEN/comments",
        "内容票据: /content/c/RAW_CONTENT_TICKET/index.html",
        "下载票据: /api/v1/downloads/RAW_DOWNLOAD_TICKET",
        "签名参数: /share-api/v1/downloads/RAW_DOWNLOAD_TICKET?X-Amz-Signature=SECRET",
        "密码字段: password=SuperSecret123",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.tokenvalue"
    })
    void sanitizeRedactsCredentials(String input) {
        String safe = AuditService.sanitize(input);
        assertThat(safe).doesNotContain("RAW_", "SECRET", "tokenvalue", "SuperSecret123");
        assertThat(safe).contains("<redacted>");
    }

    @Test
    void sanitizeKeepsNormalContent() {
        String safe = AuditService.sanitize("创建原型: 首页改版");
        assertThat(safe).isEqualTo("创建原型: 首页改版");
    }

    @Test
    void recordPersistsSanitizedSummaryAndNeverRawSecrets() {
        when(repository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLogEntity saved = auditService.record(
            "SHARE_CREATE", "SHARE", "01SHARE000000000000000001", "SUCCESS", 1L,
            "创建分享: https://preview.corp.test/s/RAW_SHARE_TOKEN?password=letmein"
        );

        assertThat(saved.getAction()).isEqualTo("SHARE_CREATE");
        assertThat(saved.getSummary())
            .doesNotContain("RAW_SHARE_TOKEN", "letmein")
            .contains("<redacted>");
        assertThat(List.of(saved.getTraceId(), saved.getSummary()))
            .noneMatch(s -> s.contains("RAW_SHARE_TOKEN"));
    }

    @Test
    void aspectRecordsSuccessAndFailureThroughAuditService() throws Throwable {
        when(repository.save(any(AuditLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        AuditAspect aspect = new AuditAspect(auditService);
        Audited audited = SampleService.class.getDeclaredMethod("create").getAnnotation(Audited.class);
        org.aspectj.lang.Signature signature = org.mockito.Mockito.mock(org.aspectj.lang.Signature.class);
        when(signature.getName()).thenReturn("create");

        org.aspectj.lang.ProceedingJoinPoint successJoin = org.mockito.Mockito.mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(successJoin.getArgs()).thenReturn(new Object[]{
            new com.company.prototype.api.security.CurrentUser(1L, "u", "admin", "管理员", java.util.Set.of("ADMIN"), false),
            "01TARGET"
        });
        when(successJoin.getSignature()).thenReturn(signature);
        when(successJoin.proceed()).thenReturn("ok");
        assertThat(aspect.around(successJoin, audited)).isEqualTo("ok");

        org.aspectj.lang.ProceedingJoinPoint failJoin = org.mockito.Mockito.mock(org.aspectj.lang.ProceedingJoinPoint.class);
        when(failJoin.getArgs()).thenReturn(new Object[]{"01TARGET"});
        when(failJoin.getSignature()).thenReturn(signature);
        when(failJoin.proceed()).thenThrow(new IllegalStateException("boom"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aspect.around(failJoin, audited))
            .isInstanceOf(IllegalStateException.class);
    }

    static class SampleService {
        @Audited(action = "SHARE_CREATE", targetType = "SHARE")
        void create() {}
    }
}
