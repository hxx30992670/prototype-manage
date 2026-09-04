package com.company.prototype.api.version;

import com.company.prototype.api.preview.PreviewTicketService;
import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/prototypes/{prototypeId}")
public class VersionController {

    private final VersionService versionService;
    private final PreviewTicketService previewTicketService;

    public VersionController(VersionService versionService, PreviewTicketService previewTicketService) {
        this.versionService = versionService;
        this.previewTicketService = previewTicketService;
    }

    @PostMapping("/preview-ticket")
    public ResponseEntity<Map<String, Object>> issueCurrentPreviewTicket(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId
    ) {
        var resp = previewTicketService.issueTicketForCurrent(user, prototypeId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/versions/{versionId}/preview-ticket")
    public ResponseEntity<Map<String, Object>> issueVersionPreviewTicket(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId
    ) {
        var resp = previewTicketService.issueTicketForVersion(user, prototypeId, versionId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/versions")
    public ResponseEntity<Map<String, Object>> createVersion(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @Valid @RequestBody VersionDtos.CreateVersionRequest req
    ) {
        var resp = versionService.createVersion(user, prototypeId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", resp));
    }

    @GetMapping("/versions")
    public ResponseEntity<Map<String, Object>> listVersions(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId
    ) {
        List<VersionDtos.VersionItemResponse> versions = versionService.listVersions(user, prototypeId);
        return ResponseEntity.ok(Map.of("data", versions));
    }

    @GetMapping("/versions/{versionId}")
    public ResponseEntity<Map<String, Object>> getVersion(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId
    ) {
        var resp = versionService.getVersion(user, prototypeId, versionId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @GetMapping("/versions/{versionId}/publish-job")
    public ResponseEntity<Map<String, Object>> getPublishJobStatus(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId
    ) {
        var resp = versionService.getPublishJobStatus(user, prototypeId, versionId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/versions/{versionId}/switch")
    public ResponseEntity<Map<String, Object>> switchCurrent(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId,
        @Valid @RequestBody VersionDtos.SwitchCurrentVersionRequest req
    ) {
        var resp = versionService.switchCurrent(user, prototypeId, versionId, req);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/versions/{versionId}/download-ticket")
    public ResponseEntity<Map<String, Object>> createDownloadTicket(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId
    ) {
        var resp = versionService.createDownloadTicket(user, prototypeId, versionId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @DeleteMapping("/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("versionId") String versionId
    ) {
        versionService.deleteVersion(user, prototypeId, versionId);
        return ResponseEntity.noContent().build();
    }
}
