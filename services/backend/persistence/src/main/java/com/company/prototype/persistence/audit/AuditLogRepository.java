package com.company.prototype.persistence.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>, JpaSpecificationExecutor<AuditLogEntity> {
    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuditLogEntity> findTop10ByOrderByCreatedAtDesc();
}
