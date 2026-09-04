package com.company.prototype.persistence.version;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrototypeVersionRepository extends JpaRepository<PrototypeVersionEntity, Long> {

    Optional<PrototypeVersionEntity> findByPublicId(String publicId);

    Optional<PrototypeVersionEntity> findByPrototypeIdAndVersionNo(Long prototypeId, Integer versionNo);

    @EntityGraph(attributePaths = {"createdBy"})
    Optional<PrototypeVersionEntity> findByPrototypeIdAndPublicId(Long prototypeId, String publicId);

    @EntityGraph(attributePaths = {"createdBy"})
    List<PrototypeVersionEntity> findAllByPrototypeIdOrderByVersionNoDesc(Long prototypeId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM PrototypeVersionEntity v WHERE v.prototype.id = :prototypeId")
    Integer findMaxVersionNoByPrototypeId(@Param("prototypeId") Long prototypeId);

    void deleteByPrototypeId(Long prototypeId);

    java.util.List<PrototypeVersionEntity> findAllByStatus(VersionStatus status);
}
