package com.company.prototype.persistence.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PrototypeCommentRepository extends JpaRepository<PrototypeCommentEntity, Long>, JpaSpecificationExecutor<PrototypeCommentEntity> {

    @EntityGraph(attributePaths = {"prototype", "version", "parent", "authorUser", "resolvedBy"})
    Optional<PrototypeCommentEntity> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"prototype", "version", "authorUser", "resolvedBy", "replies", "replies.authorUser"})
    List<PrototypeCommentEntity> findAllByPrototypeIdAndParentIsNullOrderByCreatedAtDesc(Long prototypeId);

    @EntityGraph(attributePaths = {"prototype", "version", "authorUser", "resolvedBy", "replies", "replies.authorUser"})
    List<PrototypeCommentEntity> findAllByPrototypeIdAndVersionIdAndParentIsNullOrderByCreatedAtDesc(Long prototypeId, Long versionId);

    long countByPrototypeIdAndStatusAndParentIsNull(Long prototypeId, String status);

    void deleteByPrototypeId(Long prototypeId);
}
