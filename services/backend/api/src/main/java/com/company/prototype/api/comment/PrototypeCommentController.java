package com.company.prototype.api.comment;

import com.company.prototype.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prototypes/{prototypeId}/comments")
public class PrototypeCommentController {

    private final PrototypeCommentService commentService;

    public PrototypeCommentController(PrototypeCommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createComment(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody CommentDtos.CreateCommentRequest req,
        HttpServletRequest request
    ) {
        String clientIp = request.getRemoteAddr();
        var resp = commentService.createComment(user, prototypeId, req, idempotencyKey, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", resp));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listComments(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @RequestParam(name = "versionId", required = false) String versionId,
        @RequestParam(name = "status", required = false) String status
    ) {
        var list = commentService.listComments(user, prototypeId, versionId, status);
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/{commentId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveComment(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("commentId") String commentId,
        @Valid @RequestBody CommentDtos.ResolveCommentRequest req
    ) {
        var resp = commentService.resolveComment(user, commentId, req);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @PathVariable("commentId") String commentId
    ) {
        commentService.deleteComment(user, commentId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
