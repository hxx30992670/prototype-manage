package com.company.prototype.persistence.prototype;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface PrototypeRepository extends JpaRepository<PrototypeEntity, Long>, JpaSpecificationExecutor<PrototypeEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PrototypeEntity p WHERE p.id = :id")
    Optional<PrototypeEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<PrototypeEntity> findByPublicId(String publicId);
    Optional<PrototypeEntity> findByPublicIdAndDeletedAtIsNull(String publicId);

    Optional<PrototypeEntity> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    Page<PrototypeEntity> findAllByDeletedAtIsNull(Pageable pageable);
    java.util.List<PrototypeEntity> findAllByDeletedAtIsNull();

    Page<PrototypeEntity> findAllByOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);

    Page<PrototypeEntity> findAllByCreatedByIdAndDeletedAtIsNull(Long createdById, Pageable pageable);

    // Recycle bin queries
    Optional<PrototypeEntity> findByPublicIdAndDeletedAtIsNotNull(String publicId);

    Page<PrototypeEntity> findAllByDeletedAtIsNotNull(Pageable pageable);

    Page<PrototypeEntity> findAllByCreatedByIdAndDeletedAtIsNotNull(Long createdById, Pageable pageable);

    java.util.List<PrototypeEntity> findAllByDeletedAtIsNotNullAndDeletedAtBefore(java.time.Instant deletedAt);
}
