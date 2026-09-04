package com.company.prototype.api.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.Map;

public final class UploadDtos {

    private UploadDtos() {}

    public record CreateUploadRequest(
        @NotBlank String filename,
        @NotNull @Positive Long claimedSize,
        @NotBlank String fileType
    ) {}

    public record CreateUploadResponse(
        String uploadId,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders
    ) {}

    public record CompleteUploadRequest(
        String checksum
    ) {}

    public record UploadInfoResponse(
        String uploadId,
        String filename,
        String fileType,
        Long claimedSize,
        Long actualSize,
        String checksum,
        String status,
        Instant expiresAt,
        Instant createdAt
    ) {}
}
