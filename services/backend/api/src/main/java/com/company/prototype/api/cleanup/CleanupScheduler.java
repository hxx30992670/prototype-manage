package com.company.prototype.api.cleanup;

import com.company.prototype.api.admin.AdminConfigService;
import com.company.prototype.persistence.attachment.PrototypeAttachmentEntity;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.publish.TemporaryUploadEntity;
import com.company.prototype.persistence.publish.TemporaryUploadRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import com.company.prototype.common.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 清理调度器（API 模块）。
 * - 领取并执行 PENDING/RETRYING 的 cleanup_task（Worker 不领取 PURGE 任务）；
 * - 每日执行一致性扫描。
 */
@Component
@EnableScheduling
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);
    private static final long ORPHAN_TEMP_MINUTES = 24 * 60;
    private static final int MAX_TASKS_PER_TICK = 10;

    private final CleanupTaskRepository cleanupTaskRepository;
    private final TemporaryUploadRepository temporaryUploadRepository;
    private final PrototypeRepository prototypeRepository;
    private final PrototypeAttachmentRepository attachmentRepository;
    private final PrototypeVersionRepository versionRepository;
    private final ObjectStorage objectStorage;
    private final AdminConfigService configService;
    private final PurgeTaskExecutor purgeTaskExecutor;

    public CleanupScheduler(
        CleanupTaskRepository cleanupTaskRepository,
        TemporaryUploadRepository temporaryUploadRepository,
        PrototypeRepository prototypeRepository,
        PrototypeAttachmentRepository attachmentRepository,
        PrototypeVersionRepository versionRepository,
        ObjectStorage objectStorage,
        AdminConfigService configService,
        PurgeTaskExecutor purgeTaskExecutor
    ) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.temporaryUploadRepository = temporaryUploadRepository;
        this.prototypeRepository = prototypeRepository;
        this.attachmentRepository = attachmentRepository;
        this.versionRepository = versionRepository;
        this.objectStorage = objectStorage;
        this.configService = configService;
        this.purgeTaskExecutor = purgeTaskExecutor;
    }

    @Scheduled(fixedDelayString = "${cleanup.polling.interval-ms:5000}")
    public void pollCleanupTasks() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(Duration.ofMinutes(10));
        List<CleanupTaskEntity> tasks = cleanupTaskRepository.findClaimable(
            List.of(CleanupTaskStatus.PENDING, CleanupTaskStatus.RETRYING), now, staleBefore);
        int processed = 0;
        for (CleanupTaskEntity task : tasks) {
            if (processed >= MAX_TASKS_PER_TICK) {
                break;
            }
            try {
                purgeTaskExecutor.run(task.getId());
                processed++;
            } catch (Exception e) {
                log.error("Error executing cleanup task {}: {}", task.getId(), e.getMessage(), e);
            }
        }
    }

    /** 每日一致性扫描：孤儿临时上传清理、超期回收站创建 purge 任务、DB 引用缺失对象报告。 */
    @Scheduled(cron = "${cleanup.consistency-cron:0 30 2 * * *}")
    @Transactional
    public void consistencyScan() {
        Instant now = Instant.now();
        log.info("Starting daily consistency scan at {}", now);

        int orphanTempCleaned = cleanupOrphanTemporaryUploads(now);
        int expiredRecycleCreated = createPurgeTasksForExpiredRecycle(now);
        int missingObjects = reportMissingObjects(now);

        log.info("Consistency scan finished: orphanTemporaryCleaned={}, expiredRecyclePurgeTasks={}, missingObjectRefs={}",
            orphanTempCleaned, expiredRecycleCreated, missingObjects);
    }

    /** 清理超过 24 小时仍未完成的临时上传（对象 + 记录）。 */
    private int cleanupOrphanTemporaryUploads(Instant now) {
        Instant threshold = now.minusSeconds(ORPHAN_TEMP_MINUTES * 60);
        List<TemporaryUploadEntity> orphans = temporaryUploadRepository
            .findAllByStatusNotAndCreatedAtBefore("COMPLETED", threshold);
        int cleaned = 0;
        for (TemporaryUploadEntity upload : orphans) {
            try {
                objectStorage.deletePrefix(upload.getObjectKey());
                temporaryUploadRepository.delete(upload);
                cleaned++;
            } catch (Exception e) {
                log.warn("Failed to clean orphan temporary upload {}: {}", upload.getPublicId(), e.getMessage());
            }
        }
        return cleaned;
    }

    /** 超过回收站保留天数的原型创建 PURGE_PROTOTYPE 任务（避免重复创建）。 */
    private int createPurgeTasksForExpiredRecycle(Instant now) {
        long retentionDays = 30;
        try {
            retentionDays = configService.getLong("recycle.retentionDays");
        } catch (Exception e) {
            log.warn("Failed to read recycle.retentionDays, using default 30: {}", e.getMessage());
        }
        Instant threshold = now.minusSeconds(retentionDays * 24 * 3600);
        List<PrototypeEntity> expired = prototypeRepository.findAllByDeletedAtIsNotNullAndDeletedAtBefore(threshold);
        int created = 0;
        for (PrototypeEntity prototype : expired) {
            boolean exists = cleanupTaskRepository.findAll().stream().anyMatch(t ->
                "PURGE_PROTOTYPE".equals(t.getTaskType())
                    && prototype.getPublicId().equals(t.getTargetId())
                    && t.getStatus() != CleanupTaskStatus.SUCCEEDED
                    && t.getStatus() != CleanupTaskStatus.DEAD);
            if (exists) {
                continue;
            }
            CleanupTaskEntity task = new CleanupTaskEntity();
            task.setTaskType("PURGE_PROTOTYPE");
            task.setTargetType("PROTOTYPE");
            task.setTargetId(prototype.getPublicId());
            task.setStatus(CleanupTaskStatus.PENDING);
            task.setCreatedAt(Instant.now());
            task.setNextAttemptAt(Instant.now());
            cleanupTaskRepository.save(task);
            created++;
        }
        return created;
    }

    /** 检查数据库引用的关键对象是否缺失，缺失仅记录告警日志，不自动删除任何记录。 */
    private int reportMissingObjects(Instant now) {
        int missing = 0;

        // 附件对象缺失
        List<PrototypeAttachmentEntity> attachments = attachmentRepository.findAllByDeletedAtIsNull();
        for (PrototypeAttachmentEntity a : attachments) {
            try {
                if (objectStorage.stat(a.getObjectKey()) == null) {
                    log.error("Consistency: attachment object missing key={} attachmentPublicId={} prototypeId={}",
                        a.getObjectKey(), a.getPublicId(), a.getPrototype().getId());
                    missing++;
                }
            } catch (Exception e) {
                log.warn("Consistency: failed to stat attachment {}: {}", a.getObjectKey(), e.getMessage());
            }
        }

        // 封面对象缺失
        for (PrototypeEntity p : prototypeRepository.findAllByDeletedAtIsNull()) {
            if (p.getCoverObjectKey() == null) {
                continue;
            }
            try {
                if (objectStorage.stat(p.getCoverObjectKey()) == null) {
                    log.error("Consistency: cover object missing key={} prototype={}", p.getCoverObjectKey(), p.getPublicId());
                    missing++;
                }
            } catch (Exception e) {
                log.warn("Consistency: failed to stat cover {}: {}", p.getCoverObjectKey(), e.getMessage());
            }
        }

        // 已发布版本入口文件缺失
        List<PrototypeVersionEntity> published = versionRepository
            .findAllByStatus(com.company.prototype.persistence.version.VersionStatus.PUBLISHED);
        for (PrototypeVersionEntity v : published) {
            if (v.getPublishPrefix() == null || v.getEntryPath() == null) {
                continue;
            }
            String entryKey = v.getPublishPrefix() + v.getEntryPath();
            try {
                if (objectStorage.stat(entryKey) == null) {
                    log.error("Consistency: published entry missing key={} versionId={}", entryKey, v.getId());
                    missing++;
                }
            } catch (Exception e) {
                log.warn("Consistency: failed to stat entry {}: {}", entryKey, e.getMessage());
            }
        }

        if (missing > 0) {
            log.warn("Consistency scan found {} missing object references", missing);
        }
        return missing;
    }
}
