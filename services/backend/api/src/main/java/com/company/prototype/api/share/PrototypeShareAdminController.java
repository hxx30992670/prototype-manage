package com.company.prototype.api.share;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prototypes/{prototypeId}/shares")
public class PrototypeShareAdminController {

    private final PrototypeShareService shareService;

    public PrototypeShareAdminController(PrototypeShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createShareLink(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody ShareDtos.CreateShareRequest req
    ) {
        var resp = shareService.createShareLink(user, prototypeId, req, idempotencyKey);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listShareLinks(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId
    ) {
        var list = shareService.listShareLinks(user, prototypeId);
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/{shareId}/rotate-token")
    public ResponseEntity<Map<String, Object>> rotateToken(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("shareId") String shareId
    ) {
        var resp = shareService.rotateToken(user, shareId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/{shareId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("shareId") String shareId,
        @RequestBody ShareDtos.ResetPasswordRequest req
    ) {
        shareService.resetPassword(user, shareId, req.newPassword());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{shareId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("shareId") String shareId,
        @Valid @RequestBody ShareDtos.UpdateStatusRequest req
    ) {
        shareService.updateStatus(user, shareId, req.status());
        return ResponseEntity.ok(Map.of("success", true));
    }
}
