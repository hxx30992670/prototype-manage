package com.company.prototype.api.admin;

import java.util.List;

public final class AdminConfigDtos {

    private AdminConfigDtos() {}

    public record ConfigItemResponse(
        String key,
        String value,
        String defaultValue,
        Long hardMaximum,
        String valueType,
        String description
    ) {}

    public record UpdateConfigRequest(String value) {}

    public record TagMergeRequest(String targetTagName) {}

    public record TagMergeResponse(
        String tagId,
        String tagName,
        long affectedPrototypeCount,
        long mergedTagCount
    ) {}

    public record UserStatusChangeResult(String publicId, String status, int sessionsExpired, int ticketsRevoked) {}

    public record PagedResponse<T>(List<T> records, long page, long pageSize, long total) {}
}
