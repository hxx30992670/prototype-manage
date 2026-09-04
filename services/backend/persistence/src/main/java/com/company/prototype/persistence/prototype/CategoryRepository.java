package com.company.prototype.persistence.prototype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByCode(String code);
    boolean existsByCode(String code);
    List<CategoryEntity> findAllByOrderBySortNoAsc();
}
