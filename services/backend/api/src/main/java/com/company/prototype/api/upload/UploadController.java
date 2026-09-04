package com.company.prototype.api.upload;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
        CurrentUser user,
        @Valid @RequestBody UploadDtos.CreateUploadRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UploadDtos.CreateUploadResponse res = uploadService.create(user, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", res));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
        CurrentUser user,
        @PathVariable("id") String uploadId,
        @RequestBody(required = false) UploadDtos.CompleteUploadRequest request
    ) {
        String checksum = request != null ? request.checksum() : null;
        UploadDtos.UploadInfoResponse res = uploadService.complete(user, uploadId, checksum);
        return ResponseEntity.ok(Map.of("data", res));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
        CurrentUser user,
        @PathVariable("id") String uploadId
    ) {
        UploadDtos.UploadInfoResponse res = uploadService.getUploadInfo(user, uploadId);
        return ResponseEntity.ok(Map.of("data", res));
    }
}
