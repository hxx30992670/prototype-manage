package com.company.prototype.api.recycle;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.cleanup.CleanupTaskRepository;
import com.company.prototype.persistence.cleanup.CleanupTaskStatus;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 回收站：列表、恢复、彻底删除。
 * 创建者只能看到自己删除的记录；恢复与彻底删除仅管理员。
 */
@Service
public class RecycleService {

    private final PrototypeRepository prototypeRepository;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final AuditLogRepository auditLogRepository;
    private final PrototypeAuthorizationService authorizationService;

    public RecycleService(
        PrototypeRepository prototypeRepository,
        CleanupTaskRepository cleanupTaskRepository,
        AuditLogRepository auditLogRepository,
        PrototypeAuthorizationService authorizationService
    ) {
        this.prototypeRepository = prototypeRepository;
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public Page<PrototypeEntity> listRecycled(CurrentUser currentUser, int page, int pageSize) {
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.min(Math.max(pageSize, 1), 100));
        if (currentUser.isAdmin()) {
            return prototypeRepository.findAllByDeletedAtIsNotNull(pageable);
        }
        return prototypeRepository.findAllByCreatedByIdAndDeletedAtIsNotNull(currentUser.id(), pageable);
    }

    /** 管理员恢复：清除 deleted_at；分享保持停用，需人工重新启用。 */
    @Transactional
    public void restorePrototype(String publicId, CurrentUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNotNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "回收站中不存在该原型"));

        entity.setDeletedAt(null);
        entity.setUpdatedAt(Instant.now());
        prototypeRepository.save(entity);

        logAudit("PROTOTYPE_RESTORE", entity.getPublicId(), currentUser.id(),
            "恢复原型，分享链接保持停用状态");
    }

    /** 管理员彻底删除：创建 PURGE_PROTOTYPE 异步任务，由调度器执行对象与数据清理。 */
    @Transactional
    public CleanupTaskEntity purgePrototype(String publicId, CurrentUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        PrototypeEntity entity = prototypeRepository.findByPublicIdAndDeletedAtIsNotNull(publicId)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "回收站中不存在该原型"));

        CleanupTaskEntity task = new CleanupTaskEntity();
        task.setTaskType("PURGE_PROTOTYPE");
        task.setTargetType("PROTOTYPE");
        task.setTargetId(entity.getPublicId());
        task.setStatus(CleanupTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setCreatedAt(Instant.now());
        task.setNextAttemptAt(Instant.now());
        task = cleanupTaskRepository.save(task);

        logAudit("PROTOTYPE_PURGE", entity.getPublicId(), currentUser.id(),
            "创建彻底删除任务 #" + task.getId() + "（对象异步清理）");
        return task;
    }

    private void logAudit(String action, String targetId, Long actorId, String summary) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        audit.setActorType("USER");
        audit.setActorId(actorId);
        audit.setAction(action);
        audit.setTargetType("PROTOTYPE");
        audit.setTargetId(targetId);
        audit.setResult("SUCCESS");
        audit.setSummary(summary);
        audit.setCreatedAt(Instant.now());
        auditLogRepository.save(audit);
    }
}
