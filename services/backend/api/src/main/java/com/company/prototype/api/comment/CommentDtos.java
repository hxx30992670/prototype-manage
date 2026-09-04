package com.company.prototype.api.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class CommentDtos {

    public record CreateCommentRequest(
        @NotBlank String versionPublicId,
        String parentCommentPublicId,
        @NotBlank String content
    ) {}

    public record ResolveCommentRequest(
        @NotBlank String status, // OPEN, RESOLVED
        String resolveNote,
        @NotNull Long rowVersion
    ) {}

    public record CommentReplyItem(
        String publicId,
        String content,
        String authorType,
        String authorName,
        Instant createdAt,
        boolean isDeleted
    ) {}

    public record CommentItemResponse(
        String publicId,
        String prototypePublicId,
        String versionPublicId,
        Integer versionNo,
        String content,
        String status,
        String authorType,
        String authorName,
        String resolvedBy,
        Instant resolvedAt,
        String resolveNote,
        Long rowVersion,
        Instant createdAt,
        boolean isDeleted,
        List<CommentReplyItem> replies
    ) {}
}
