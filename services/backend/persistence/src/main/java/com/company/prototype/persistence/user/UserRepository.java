package com.company.prototype.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByPublicId(String publicId);
    List<UserEntity> findByPublicIdIn(Collection<String> publicIds);
    boolean existsByUsername(String username);

    @Query("""
        SELECT DISTINCT u FROM UserEntity u
        JOIN u.roles r
        WHERE u.status = 'ACTIVE'
          AND r.code IN ('CREATOR', 'ADMIN')
        ORDER BY u.displayName ASC
        """)
    List<UserEntity> findActiveAssignableOwners();
}
