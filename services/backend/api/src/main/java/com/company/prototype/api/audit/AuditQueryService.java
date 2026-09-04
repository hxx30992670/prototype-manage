package com.company.prototype.api.audit;

import com.company.prototype.persistence.audit.AuditLogEntity;
import com.company.prototype.persistence.audit.AuditLogRepository;
import com.company.prototype.persistence.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditQueryService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogEntity> query(
        String actorPublicId,
        String action,
        String targetType,
        String targetId,
        String result,
        String traceId,
        Instant from,
        Instant to,
        int page,
        int pageSize
    ) {
        int validatedPage = Math.max(page, 1);
        int validatedPageSize = Math.min(Math.max(pageSize, 1), 200);

        final Long actorId;
        if (actorPublicId != null && !actorPublicId.isBlank()) {
            actorId = userRepository.findByPublicId(actorPublicId)
                .map(u -> u.getId())
                .orElse(-1L); // 未知用户，匹配不到任何记录
        } else {
            actorId = null;
        }

        Specification<AuditLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null && !targetId.isBlank()) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (traceId != null && !traceId.isBlank()) {
                predicates.add(cb.equal(root.get("traceId"), traceId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec,
            PageRequest.of(validatedPage - 1, validatedPageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
