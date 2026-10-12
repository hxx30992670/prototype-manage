package com.company.prototype.api.download;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.api.security.PrototypeAuthorizationService;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.attachment.PrototypeAttachmentEntity;
import com.company.prototype.persistence.attachment.PrototypeAttachmentRepository;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import com.company.prototype.persistence.prototype.PrototypeRepository;
import com.company.prototype.persistence.version.PrototypeVersionEntity;
import com.company.prototype.persistence.version.PrototypeVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadAuthorizationService {

    private final PrototypeRepository prototypeRepository;
    private final PrototypeVersionRepository versionRepository;
    private final PrototypeAttachmentRepository attachmentRepository;
    private final PrototypeAuthorizationService authorizationService;

    public DownloadAuthorizationService(
        PrototypeRepository prototypeRepository,
        PrototypeVersionRepository versionRepository,
        PrototypeAttachmentRepository attachmentRepository,
        PrototypeAuthorizationService authorizationService
    ) {
        this.prototypeRepository = prototypeRepository;
        this.versionRepository = versionRepository;
        this.attachmentRepository = attachmentRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public DownloadFile authorize(DownloadTicketRecord record, CurrentUser user) {
        if (user == null) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        return switch (record.type()) {
            case VERSION -> authorizeVersion(record, user);
            case ATTACHMENT -> authorizeAttachment(record, user);
        };
    }

    private DownloadFile authorizeVersion(DownloadTicketRecord record, CurrentUser user) {
        PrototypeEntity prototype = prototypeRepository.findByPublicIdAndDeletedAtIsNull(record.prototypePublicId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        if (!authorizationService.canDownload(user, prototype)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        PrototypeVersionEntity version = versionRepository.findByPrototypeIdAndPublicId(prototype.getId(), record.resourcePublicId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        if (version.getSourceObjectKey() == null || version.getSourceObjectKey().isBlank()) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "原始文件不存在");
        }
        String extension = "ZIP".equalsIgnoreCase(version.getSourceType()) ? ".zip" : ".html";
        String filename = prototype.getCode() + "-v" + version.getVersionNo() + extension;
        return new DownloadFile(version.getSourceObjectKey(), filename);
    }

    private DownloadFile authorizeAttachment(DownloadTicketRecord record, CurrentUser user) {
        PrototypeEntity prototype = prototypeRepository.findByPublicIdAndDeletedAtIsNull(record.prototypePublicId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        PrototypeAttachmentEntity attachment = attachmentRepository.findByPublicIdAndDeletedAtIsNull(record.resourcePublicId())
            .orElseThrow(() -> new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
        if (!prototype.getId().equals(attachment.getPrototype().getId())) {
            throw new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!authorizationService.canView(user, prototype)) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        return new DownloadFile(attachment.getObjectKey(), attachment.getName());
    }

    public record DownloadFile(String objectKey, String filename) {}
}
