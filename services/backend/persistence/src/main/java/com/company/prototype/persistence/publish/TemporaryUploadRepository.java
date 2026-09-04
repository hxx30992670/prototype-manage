package com.company.prototype.persistence.publish;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemporaryUploadRepository extends JpaRepository<TemporaryUploadEntity, Long> {

    Optional<TemporaryUploadEntity> findByPublicId(String publicId);

    java.util.List<TemporaryUploadEntity> findAllByStatusNotAndCreatedAtBefore(String status, java.time.Instant createdAt);
}
