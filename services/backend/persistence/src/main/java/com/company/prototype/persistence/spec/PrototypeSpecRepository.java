package com.company.prototype.persistence.spec;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrototypeSpecRepository extends JpaRepository<PrototypeSpecEntity, Long> {

    @EntityGraph(attributePaths = {"updatedBy"})
    Optional<PrototypeSpecEntity> findByPrototypeId(Long prototypeId);

    void deleteByPrototypeId(Long prototypeId);
}
