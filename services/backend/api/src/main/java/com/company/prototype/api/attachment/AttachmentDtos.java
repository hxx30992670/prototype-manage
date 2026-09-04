package com.company.prototype.api.attachment;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class AttachmentDtos {

    public record CreateAttachmentRequest(
        @NotBlank String uploadId,
        @NotBlank String name,
        @NotBlank String type, // IMAGE, DOCUMENT, ICON, OTHER
        String purpose,
        @NotBlank String accessScope, // INTERNAL, PUBLIC
        String versionPublicId
    ) {}

    public record AttachmentItemResponse(
        String publicId,
        String prototypePublicId,
        String versionPublicId,
        Integer versionNo,
        String name,
        String type,
        String purpose,
        String accessScope,
        Long size,
        String mimeType,
        String createdBy,
        Instant createdAt
    ) {}

    public record UploadCoverRequest(
        @NotBlank String uploadId
    ) {}

    public record DownloadTicketResponse(
        String url,
        Instant expiresAt
    ) {}
}
