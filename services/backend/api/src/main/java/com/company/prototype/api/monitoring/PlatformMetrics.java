package com.company.prototype.api.monitoring;

import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.publish.PublishJobStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台自定义指标：
 * - 登录失败、分享密码失败、MinIO 错误、一致性异常计数器；
 * - 发布任务积压、清理任务积压 gauge（API 侧按数据库任务表统计，
 *   因为 Worker 按设计不开放 HTTP 端口，不暴露自身指标端点）。
 */
@Component
public class PlatformMetrics {

    private static final Logger log = LoggerFactory.getLogger(PlatformMetrics.class);

    private final MeterRegistry meterRegistry;
    private final PublishJobRepository publishJobRepository;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final java.util.concurrent.atomic.AtomicLong publishPending = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong publishFailed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong cleanupPending = new java.util.concurrent.atomic.AtomicLong();

    public PlatformMetrics(
        MeterRegistry meterRegistry,
        PublishJobRepository publishJobRepository,
        CleanupTaskRepository cleanupTaskRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.publishJobRepository = publishJobRepository;
        this.cleanupTaskRepository = cleanupTaskRepository;
        // 惰性 gauge：注册一次，值由 AtomicLong 提供
        io.micrometer.core.instrument.Gauge.builder("prototype_publish_jobs_pending", publishPending,
            java.util.concurrent.atomic.AtomicLong::get).register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder("prototype_publish_jobs_failed", publishFailed,
            java.util.concurrent.atomic.AtomicLong::get).register(meterRegistry);
        io.micrometer.core.instrument.Gauge.builder("prototype_cleanup_jobs_pending", cleanupPending,
            java.util.concurrent.atomic.AtomicLong::get).register(meterRegistry);
    }

    public void loginFailure() {
        counter("prototype_login_failures_total").increment();
    }

    public void sharePasswordFailure() {
        counter("prototype_share_password_failures_total").increment();
    }

    public void minioError() {
        counter("prototype_minio_errors_total").increment();
    }

    public void consistencyException() {
        counter("prototype_consistency_exceptions_total").increment();
    }

    public void cleanupTaskDead() {
        counter("prototype_cleanup_tasks_dead_total").increment();
    }

    private Counter counter(String name) {
        return meterRegistry.counter(name);
    }

    @Scheduled(fixedDelayString = "${metrics.gauge.interval-ms:30000}")
    public void refreshGauges() {
        try {
            publishPending.set(publishJobRepository.countByStatusIn(
                List.of(PublishJobStatus.PENDING, PublishJobStatus.RUNNING, PublishJobStatus.RETRYING)));
            publishFailed.set(publishJobRepository.countByStatus(PublishJobStatus.FAILED));
            cleanupPending.set(cleanupTaskRepository.countByStatusIn(
                List.of(CleanupTaskStatus.PENDING, CleanupTaskStatus.RUNNING, CleanupTaskStatus.RETRYING)));
        } catch (Exception e) {
            log.warn("Failed to refresh metric gauges: {}", e.getMessage());
        }
    }
}
