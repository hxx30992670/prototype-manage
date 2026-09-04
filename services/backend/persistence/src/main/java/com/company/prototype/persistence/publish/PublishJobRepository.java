package com.company.prototype.persistence.publish;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublishJobRepository extends JpaRepository<PublishJobEntity, Long> {

    Optional<PublishJobEntity> findByVersionId(Long versionId);

    boolean existsByPrototypeIdAndStatusIn(Long prototypeId, Collection<PublishJobStatus> statuses);

    List<PublishJobEntity> findTop10ByStatusOrderByCreatedAtAsc(PublishJobStatus status);

    @Query("""
        SELECT j FROM PublishJobEntity j
        WHERE j.status = com.company.prototype.persistence.publish.PublishJobStatus.PENDING
           OR (j.status = com.company.prototype.persistence.publish.PublishJobStatus.RUNNING
               AND (j.lockedAt IS NULL OR j.lockedAt < :staleBefore))
        ORDER BY j.createdAt ASC
        """)
    List<PublishJobEntity> findClaimable(@Param("staleBefore") Instant staleBefore, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE publish_job
        SET status = 'RUNNING', locked_at = :now, attempt_count = attempt_count + 1
        WHERE id = :id
          AND (
            status = 'PENDING'
            OR (status = 'RUNNING' AND (locked_at IS NULL OR locked_at < :staleBefore))
          )
        """, nativeQuery = true)
    int tryClaim(@Param("id") Long id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    void deleteByPrototypeId(Long prototypeId);

    long countByStatusIn(java.util.Collection<PublishJobStatus> statuses);

    long countByStatus(PublishJobStatus status);
}
