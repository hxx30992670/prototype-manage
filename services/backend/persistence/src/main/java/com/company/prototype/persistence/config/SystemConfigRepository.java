package com.company.prototype.persistence.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {
    Optional<SystemConfigEntity> findByConfigKey(String configKey);
}
