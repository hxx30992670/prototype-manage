package com.company.prototype.persistence.prototype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {
    Optional<TagEntity> findByName(String name);
    boolean existsByName(String name);
    Set<TagEntity> findByNameIn(Set<String> names);

    /** 把引用 sourceTagId 且未引用 targetTagId 的原型改写为 targetTagId；返回实际改写行数。 */
    @Modifying
    @Query(value = """
        UPDATE prototype_tag_rel r
        LEFT JOIN prototype_tag_rel t
          ON t.prototype_id = r.prototype_id AND t.tag_id = :targetId
        SET r.tag_id = :targetId
        WHERE r.tag_id = :sourceId AND t.prototype_id IS NULL
        """, nativeQuery = true)
    int rebindPrototypes(@Param("sourceId") long sourceId, @Param("targetId") long targetId);

    /** 统计引用某标签的原型数量。 */
    @Query(value = "SELECT COUNT(DISTINCT prototype_id) FROM prototype_tag_rel WHERE tag_id = :tagId", nativeQuery = true)
    long countPrototypesByTag(@Param("tagId") long tagId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM prototype_tag_rel WHERE prototype_id = :prototypeId", nativeQuery = true)
    void deletePrototypeTagRel(@org.springframework.data.repository.query.Param("prototypeId") long prototypeId);
}
