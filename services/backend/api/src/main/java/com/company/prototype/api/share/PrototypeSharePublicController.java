package com.company.prototype.api.share;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/share-api/v1/shares/{token}")
public class PrototypeSharePublicController {

    private final PrototypeShareService shareService;
    private final com.company.prototype.api.comment.PrototypeCommentService commentService;

    public PrototypeSharePublicController(
        PrototypeShareService shareService,
        com.company.prototype.api.comment.PrototypeCommentService commentService
    ) {
        this.shareService = shareService;
        this.commentService = commentService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap(
        @PathVariable("token") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String sessionCookie = extractSessionCookie(request);
        var result = shareService.bootstrapShare(token, sessionCookie);
        addShareSessionCookie(request, response, result.sessionId(), result.ttl());
        return ResponseEntity.ok(Map.of("data", result.response()));
    }

    @PostMapping({"/verify-password", "/verify"})
    public ResponseEntity<Map<String, Object>> verifyPassword(
        @PathVariable("token") String token,
        @Valid @RequestBody ShareDtos.VerifyPasswordRequest req,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var result = shareService.verifyPassword(token, req, request.getRemoteAddr());

        addShareSessionCookie(request, response, result.sessionId(), result.ttl());
        return ResponseEntity.ok(Map.of("data", result.response()));
    }

    @PostMapping("/content-ticket")
    public ResponseEntity<Map<String, Object>> issueContentTicket(
        @PathVariable("token") String token,
        @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrfToken,
        HttpServletRequest request
    ) {
        String sessionCookie = extractSessionCookie(request);
        var resp = shareService.issueContentTicket(token, sessionCookie, csrfToken);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @GetMapping("/comments")
    public ResponseEntity<Map<String, Object>> listComments(
        @PathVariable("token") String token,
        HttpServletRequest request
    ) {
        String sessionCookie = extractSessionCookie(request);
        var list = commentService.listPublicShareComments(token, sessionCookie);
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/attachments/{attachmentId}/download-ticket")
    public ResponseEntity<Map<String, Object>> issueAttachmentDownloadTicket(
        @PathVariable("token") String token,
        @PathVariable("attachmentId") String attachmentId,
        HttpServletRequest request
    ) {
        String sessionCookie = extractSessionCookie(request);
        var resp = shareService.issuePublicAttachmentDownloadTicket(token, attachmentId, sessionCookie);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PostMapping("/comments")
    public ResponseEntity<Map<String, Object>> createGuestComment(
        @PathVariable("token") String token,
        @RequestHeader(value = "X-CSRF-TOKEN", required = false) String csrfToken,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody com.company.prototype.api.comment.CommentDtos.CreateCommentRequest req,
        HttpServletRequest request
    ) {
        String sessionCookie = extractSessionCookie(request);
        String clientIp = request.getRemoteAddr();
        var resp = commentService.createGuestComment(token, sessionCookie, csrfToken, req, idempotencyKey, clientIp);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(Map.of("data", resp));
    }

    private void addShareSessionCookie(
        HttpServletRequest request,
        HttpServletResponse response,
        String sessionId,
        java.time.Duration ttl
    ) {
        if (sessionId == null || ttl == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from("PH_SHARE_SESSION", sessionId)
            .path("/")
            .httpOnly(true)
            .sameSite("Lax")
            .secure(request.isSecure())
            .maxAge(ttl)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> "PH_SHARE_SESSION".equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
