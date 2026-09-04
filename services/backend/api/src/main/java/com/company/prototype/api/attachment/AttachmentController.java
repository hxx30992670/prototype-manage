package com.company.prototype.api.attachment;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/api/v1/prototypes/{prototypeId}/attachments")
    public ResponseEntity<Map<String, Object>> createAttachment(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @Valid @RequestBody AttachmentDtos.CreateAttachmentRequest req
    ) {
        var resp = attachmentService.createAttachment(user, prototypeId, req);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @GetMapping("/api/v1/prototypes/{prototypeId}/attachments")
    public ResponseEntity<Map<String, Object>> listAttachments(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId
    ) {
        var list = attachmentService.listAttachments(user, prototypeId);
        return ResponseEntity.ok(Map.of("data", list));
    }

    @GetMapping("/api/v1/attachments")
    public ResponseEntity<Map<String, Object>> listGlobalAttachments(
        @AuthenticationPrincipal CurrentUser user,
        @RequestParam(name = "prototypeId", required = false) String prototypeId,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "createdBy", required = false) String createdBy
    ) {
        var list = attachmentService.listGlobalAttachments(user, prototypeId, type, createdBy);
        return ResponseEntity.ok(Map.of("data", list));
    }

    @DeleteMapping("/api/v1/attachments/{attachmentId}")
    public ResponseEntity<Map<String, Object>> deleteAttachment(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("attachmentId") String attachmentId
    ) {
        attachmentService.deleteAttachment(user, attachmentId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/api/v1/attachments/{attachmentId}/download")
    public ResponseEntity<Map<String, Object>> createDownloadTicket(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("attachmentId") String attachmentId
    ) {
        var resp = attachmentService.createDownloadTicket(user, attachmentId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/api/v1/prototypes/{prototypeId}/cover")
    public ResponseEntity<Map<String, Object>> uploadCover(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @Valid @RequestBody AttachmentDtos.UploadCoverRequest req
    ) {
        attachmentService.uploadCover(user, prototypeId, req);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
