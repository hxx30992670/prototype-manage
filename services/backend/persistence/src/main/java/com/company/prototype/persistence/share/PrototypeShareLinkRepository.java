package com.company.prototype.persistence.share;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrototypeShareLinkRepository extends JpaRepository<PrototypeShareLinkEntity, Long> {

    @EntityGraph(attributePaths = {"prototype", "createdBy"})
    Optional<PrototypeShareLinkEntity> findByTokenDigest(String tokenDigest);

    @EntityGraph(attributePaths = {"prototype", "createdBy"})
    Optional<PrototypeShareLinkEntity> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"prototype", "createdBy"})
    List<PrototypeShareLinkEntity> findAllByPrototypeIdOrderByCreatedAtDesc(Long prototypeId);

    void deleteByPrototypeId(Long prototypeId);
}
