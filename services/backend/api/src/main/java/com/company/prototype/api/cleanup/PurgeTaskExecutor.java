package com.company.prototype.api.cleanup;

import com.company.prototype.api.preview.PreviewTicketService;
import com.company.prototype.common.storage.ObjectStorage;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.comment.PrototypeCommentRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.prototype.TagRepository;
import com.company.prototype.persistence.publish.PublishJobRepository;
import com.company.prototype.persistence.share.PrototypeShareLinkRepository;
import com.company.prototype.persistence.spec.PrototypeSpecRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * PURGE_PROTOTYPE 任务执行器（API 模块）。
 * 先删除 MinIO 对象与 Redis 票据，再删除数据库业务记录；失败按指数退避重试，
 * 超过 10 次标记 DEAD 并告警。执行幂等：任一阶段失败重跑不会破坏已删除部分。
 */
@Service
public class PurgeTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(PurgeTaskExecutor.class);
    private static final int MAX_ATTEMPTS = 10;

    private final CleanupTaskRepository cleanupTaskRepository;
    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeAttachmentRepository attachmentRepository;
    private final PrototypeCommentRepository commentRepository;
    private final PrototypeSpecRepository specRepository;
    private final PrototypeShareLinkRepository shareLinkRepository;
    private final PublishJobRepository publishJobRepository;
    private final TagRepository tagRepository;
    private final ObjectStorage objectStorage;
    private final PreviewTicketService previewTicketService;
    private final StringRedisTemplate redisTemplate;
    private final com.company.prototype.api.monitoring.PlatformMetrics platformMetrics;

    public PurgeTaskExecutor(
        CleanupTaskRepository cleanupTaskRepository,
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeAttachmentRepository attachmentRepository,
        PrototypeCommentRepository commentRepository,
        PrototypeSpecRepository specRepository,
        PrototypeShareLinkRepository shareLinkRepository,
        PublishJobRepository publishJobRepository,
        TagRepository tagRepository,
        ObjectStorage objectStorage,
        PreviewTicketService previewTicketService,
        StringRedisTemplate redisTemplate,
        @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.company.prototype.api.monitoring.PlatformMetrics platformMetrics
    ) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.attachmentRepository = attachmentRepository;
        this.commentRepository = commentRepository;
        this.specRepository = specRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.publishJobRepository = publishJobRepository;
        this.tagRepository = tagRepository;
        this.objectStorage = objectStorage;
        this.previewTicketService = previewTicketService;
        this.redisTemplate = redisTemplate;
        this.platformMetrics = platformMetrics;
    }

    @Transactional
    public void run(Long taskId) {
        CleanupTaskEntity task = cleanupTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Cleanup task {} not found", taskId);
            return;
        }
        if (task.getStatus() == CleanupTaskStatus.SUCCEEDED || task.getStatus() == CleanupTaskStatus.DEAD) {
            return;
        }

        Instant now = Instant.now();
        Instant staleBefore = now.minus(Duration.ofMinutes(10));
        if (cleanupTaskRepository.tryClaim(taskId, now, staleBefore) == 0
            && task.getStatus() != CleanupTaskStatus.RUNNING) {
            return;
        }
        task = cleanupTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }

        task.setStatus(CleanupTaskStatus.RUNNING);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setLockedAt(now);
        cleanupTaskRepository.save(task);

        try {
            if ("PURGE_PROTOTYPE".equals(task.getTaskType())) {
                purgePrototype(task);
            } else {
                throw new IllegalStateException("未知清理任务类型: " + task.getTaskType());
            }
            task.setStatus(CleanupTaskStatus.SUCCEEDED);
            task.setErrorDetail(null);
            task.setFinishedAt(Instant.now());
            cleanupTaskRepository.save(task);
            log.info("Cleanup task {} succeeded (target={})", task.getId(), task.getTargetId());
        } catch (Exception e) {
            handleFailure(task, e);
        }
    }

    private void purgePrototype(CleanupTaskEntity task) {
        PrototypeEntity prototype = prototypeRepository.findByPublicId(task.getTargetId()).orElse(null);
        Long prototypeId = prototype != null ? prototype.getId() : null;

        if (prototypeId == null) {
            // 记录已不存在（如先被手工删除），仍按 publicId 尽力清理对象前缀不可行（前缀用内部 id），
            // 直接标记成功，避免死循环。
            log.warn("Purge target prototype {} not found in DB, marking task succeeded", task.getTargetId());
            return;
        }

        // 1. 清 MinIO 对象（不可变前缀，幂等）
        objectStorage.deletePrefix("prototypes/" + prototypeId + "/versions/");
        objectStorage.deletePrefix("prototypes/" + prototypeId + "/attachments/");
        objectStorage.deletePrefix("prototypes/" + prototypeId + "/covers/");
        objectStorage.deletePrefix("covers/" + prototypeId + "/");

        // 2. 清 Redis 内容票据与分享会话索引
        List<PrototypeVersionEntity> versions = versionRepository.findAllByPrototypeIdOrderByVersionNoDesc(prototypeId);
        for (PrototypeVersionEntity v : versions) {
            previewTicketService.revokeVersionTickets(v.getId());
        }
        // 分享链接的会话/票据索引在原型删除（进回收站）时已按链接逐一撤销；此处兜底清理分享相关键
        try {
            Set<String> keys = redisTemplate.keys("share:*");
            if (keys != null && !keys.isEmpty()) {
                for (String k : keys) {
                    if (k.contains(":" + prototypeId)) {
                        redisTemplate.delete(k);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clean share redis keys for prototype {}: {}", prototypeId, e.getMessage());
        }

        // 3. 删除数据库业务记录（顺序：引用方在前）
        commentRepository.deleteByPrototypeId(prototypeId);
        shareLinkRepository.deleteByPrototypeId(prototypeId);
        attachmentRepository.deleteByPrototypeId(prototypeId);
        specRepository.deleteByPrototypeId(prototypeId);
        publishJobRepository.deleteByPrototypeId(prototypeId);
        versionRepository.deleteByPrototypeId(prototypeId);
        tagRepository.deletePrototypeTagRel(prototypeId);
        prototypeRepository.deleteById(prototypeId);
    }

    private void handleFailure(CleanupTaskEntity task, Exception e) {
        log.error("Cleanup task {} failed (attempt {}): {}", task.getId(), task.getAttemptCount(), e.getMessage());
        task.setErrorDetail(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000)) : "unknown error");
        if (task.getAttemptCount() >= MAX_ATTEMPTS) {
            task.setStatus(CleanupTaskStatus.DEAD);
            if (platformMetrics != null) {
                platformMetrics.cleanupTaskDead();
            }
            log.error("Cleanup task {} exceeded {} attempts, marked DEAD", task.getId(), MAX_ATTEMPTS);
        } else {
            task.setStatus(CleanupTaskStatus.RETRYING);
            long delaySeconds = Math.min(5L * (1L << Math.min(Math.max(task.getAttemptCount() - 1, 0), 10)), 1800L);
            task.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
        }
        cleanupTaskRepository.save(task);
    }
}
