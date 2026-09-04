package com.company.prototype.api.recycle;

import com.company.prototype.persistence.prototype.PrototypeEntity;

import java.time.Instant;

public record RecycleView(
    String publicId,
    String code,
    String name,
    String reviewStatus,
    Instant deletedAt,
    Instant createdAt
) {
    public static RecycleView from(PrototypeEntity e) {
        return new RecycleView(
            e.getPublicId(),
            e.getCode(),
            e.getName(),
            e.getReviewStatus(),
            e.getDeletedAt(),
            e.getCreatedAt()
        );
    }
}
