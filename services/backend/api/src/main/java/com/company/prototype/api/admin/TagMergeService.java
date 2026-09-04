package com.company.prototype.api.admin;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.prototype.TagEntity;
import com.company.prototype.persistence.prototype.TagRepository;
import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 管理端辅助能力：标签合并。
 * 把源标签合并到目标标签：全部原型引用改写后删除源标签，返回受影响的原型数量。
 */
@Service
public class TagMergeService {

    private final TagRepository tagRepository;
    private final AuditLogRepository auditLogRepository;

    public TagMergeService(TagRepository tagRepository, AuditLogRepository auditLogRepository) {
        this.tagRepository = tagRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AdminConfigDtos.TagMergeResponse mergeTags(String sourceTagName, String targetTagName, CurrentUser currentUser) {
        TagEntity source = tagRepository.findByName(sourceTagName)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "源标签不存在"));
        TagEntity target = tagRepository.findByName(targetTagName)
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "目标标签不存在"));

        if (source.getId().equals(target.getId())) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "不能把标签合并到自身");
        }

        long affected = tagRepository.countPrototypesByTag(source.getId());
        tagRepository.rebindPrototypes(source.getId(), target.getId());
        tagRepository.delete(source);

        AuditLogEntity audit = new AuditLogEntity();
        audit.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        audit.setActorType("USER");
        audit.setActorId(currentUser.id());
        audit.setAction("TAG_MERGE");
        audit.setTargetType("TAG");
        audit.setTargetId(target.getId().toString());
        audit.setResult("SUCCESS");
        audit.setSummary("合并标签: " + source.getName() + " -> " + target.getName() + ", 影响原型 " + affected + " 个");
        audit.setCreatedAt(Instant.now());
        auditLogRepository.save(audit);

        return new AdminConfigDtos.TagMergeResponse(
            target.getId().toString(), target.getName(), affected, 1L);
    }
}
