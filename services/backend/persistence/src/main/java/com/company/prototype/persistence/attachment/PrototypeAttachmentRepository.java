package com.company.prototype.persistence.attachment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrototypeAttachmentRepository extends JpaRepository<PrototypeAttachmentEntity, Long>, JpaSpecificationExecutor<PrototypeAttachmentEntity> {

    @EntityGraph(attributePaths = {"prototype", "version", "createdBy"})
    Optional<PrototypeAttachmentEntity> findByPublicIdAndDeletedAtIsNull(String publicId);

    @EntityGraph(attributePaths = {"prototype", "version", "createdBy"})
    List<PrototypeAttachmentEntity> findAllByPrototypeIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long prototypeId);

    void deleteByPrototypeId(Long prototypeId);

    java.util.List<PrototypeAttachmentEntity> findAllByDeletedAtIsNull();

    @Query("SELECT COALESCE(SUM(a.size), 0) FROM PrototypeAttachmentEntity a WHERE a.prototype.id = :prototypeId AND a.deletedAt IS NULL")
    long sumSizeByPrototypeId(@Param("prototypeId") Long prototypeId);
}
