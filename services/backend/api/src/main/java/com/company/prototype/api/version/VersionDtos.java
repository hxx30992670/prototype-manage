package com.company.prototype.api.version;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class VersionDtos {
    private VersionDtos() {}

    public record CreateVersionRequest(
        @NotBlank(message = "上传标识不能为空") String uploadId,
        @NotBlank(message = "文件类型不能为空") String sourceType,
        @NotBlank(message = "变更说明不能为空") String changeLog,
        String description
    ) {}

    public record CreateVersionResponse(
        String versionId,
        int versionNo,
        String status,
        Long jobId
    ) {}

    public record VersionItemResponse(
        String versionId,
        int versionNo,
        String changeLog,
        String description,
        String status,
        String sourceType,
        long sourceSize,
        String entryPath,
        int fileCount,
        long expandedSize,
        boolean isCurrent,
        String failureStage,
        String failureMessage,
        String createdBy,
        Instant createdAt,
        Instant publishedAt
    ) {}

    public record SwitchCurrentVersionRequest(
        Integer expectedCurrentVersionNo,
        @NotBlank(message = "回滚或切换原因不能为空") String reason
    ) {}

    public record PublishJobStatusResponse(
        Long jobId,
        String status,
        String stage,
        int progress,
        String errorDetail,
        Instant createdAt,
        Instant finishedAt
    ) {}

    public record DownloadTicketResponse(
        String url,
        Instant expiresAt
    ) {}
}
