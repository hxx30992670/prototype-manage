package com.company.prototype.api.share;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ShareDtos {

    public record CreateShareRequest(
        @NotBlank String name,
        String password,
        Instant expiresAt,
        String specScope,
        Boolean allowComment,
        Boolean allowPublicAttachment
    ) {}

    public record ShareCreatedResponse(
        String shareId,
        String rawUrl,
        boolean secretAvailable
    ) {}

    public record ShareLinkItemResponse(
        String shareId,
        String name,
        Instant expiresAt,
        String status,
        String specScope,
        boolean allowComment,
        boolean allowPublicAttachment,
        boolean hasPassword,
        long authEpoch,
        long visitCount,
        Instant lastVisitedAt,
        String createdBy,
        Instant createdAt
    ) {}

    public record RotateTokenResponse(
        String rawUrl,
        boolean secretAvailable
    ) {}

    public record ResetPasswordRequest(
        String newPassword
    ) {}

    public record UpdateStatusRequest(
        @NotBlank String status
    ) {}

    public record ShareSessionRecord(
        long shareLinkId,
        long authEpoch,
        String csrfTokenDigest,
        String guestName,
        Instant expiresAt
    ) {}

    public record BootstrapResponse(
        String prototypeName,
        String prototypePublicId,
        boolean requiresPassword,
        boolean authenticated,
        String csrfToken,
        String specScope,
        Map<String, Object> spec,
        List<Map<String, Object>> attachments,
        boolean allowComment,
        String statusMessage,
        String currentVersionPublicId,
        Integer currentVersionNo
    ) {}

    public record VerifyPasswordRequest(
        @NotBlank String password,
        String guestName
    ) {}

    public record VerifyPasswordResponse(
        boolean success,
        String csrfToken
    ) {}

    public record ContentTicketResponse(
        String ticket,
        String contentUrl,
        Instant expiresAt
    ) {}

    public record DownloadTicketResponse(
        String url,
        Instant expiresAt
    ) {}
}
