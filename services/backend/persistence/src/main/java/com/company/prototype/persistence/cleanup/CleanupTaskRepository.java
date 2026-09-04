package com.company.prototype.persistence.cleanup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface CleanupTaskRepository extends JpaRepository<CleanupTaskEntity, Long> {

    List<CleanupTaskEntity> findTop10ByStatusInOrderByCreatedAtAsc(List<CleanupTaskStatus> statuses);

    @Query("""
        SELECT t FROM CleanupTaskEntity t
        WHERE (
            t.status IN :statuses
            AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now)
        ) OR (
            t.status = com.company.prototype.persistence.cleanup.CleanupTaskStatus.RUNNING
            AND (t.lockedAt IS NULL OR t.lockedAt < :staleBefore)
        )
        ORDER BY t.createdAt ASC
        """)
    List<CleanupTaskEntity> findClaimable(
        @Param("statuses") List<CleanupTaskStatus> statuses,
        @Param("now") Instant now,
        @Param("staleBefore") Instant staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
        UPDATE cleanup_task
        SET status = 'RUNNING', locked_at = :now
        WHERE id = :id
          AND (
            status IN ('PENDING', 'RETRYING')
            OR (status = 'RUNNING' AND (locked_at IS NULL OR locked_at < :staleBefore))
          )
        """, nativeQuery = true)
    int tryClaim(@Param("id") Long id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);

    long countByStatusIn(java.util.Collection<CleanupTaskStatus> statuses);
}
