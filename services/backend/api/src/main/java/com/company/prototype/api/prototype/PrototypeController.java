package com.company.prototype.api.prototype;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prototypes")
public class PrototypeController {

    private final PrototypeService prototypeService;

    public PrototypeController(PrototypeService prototypeService) {
        this.prototypeService = prototypeService;
    }

    @GetMapping
    public ResponseEntity<PrototypeDtos.PrototypePageResponse> listPrototypes(
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "categoryId", required = false) String categoryId,
        @RequestParam(name = "tagId", required = false) String tagId,
        @RequestParam(name = "reviewStatus", required = false) String reviewStatus,
        @RequestParam(name = "archived", required = false) Boolean archived,
        @RequestParam(name = "createdBy", required = false) String createdBy,
        @RequestParam(name = "ownerId", required = false) String ownerId,
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(name = "sortBy", defaultValue = "updatedAt") String sortBy,
        @RequestParam(name = "sortOrder", defaultValue = "DESC") String sortOrder,
        CurrentUser currentUser
    ) {
        PrototypeDtos.PrototypePageResponse res = prototypeService.queryPrototypes(
            keyword, categoryId, tagId, reviewStatus, archived, createdBy, ownerId,
            page, pageSize, sortBy, sortOrder, currentUser
        );
        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPrototype(
        @Valid @RequestBody PrototypeDtos.CreatePrototypeRequest req,
        CurrentUser currentUser
    ) {
        PrototypeDtos.PrototypeResponse res = prototypeService.createPrototype(req, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", res));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPrototype(
        @PathVariable("id") String publicId,
        CurrentUser currentUser
    ) {
        PrototypeDtos.PrototypeResponse res = prototypeService.getPrototype(publicId, currentUser);
        return ResponseEntity.ok(Map.of("data", res));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePrototype(
        @PathVariable("id") String publicId,
        @Valid @RequestBody PrototypeDtos.UpdatePrototypeRequest req,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        CurrentUser currentUser
    ) {
        Long ifMatchVersion = parseIfMatch(ifMatch);
        PrototypeDtos.PrototypeResponse res = prototypeService.updatePrototype(publicId, req, ifMatchVersion, currentUser);
        return ResponseEntity.ok(Map.of("data", res));
    }

    @PutMapping("/{id}/review-status")
    public ResponseEntity<Map<String, Object>> updateReviewStatus(
        @PathVariable("id") String publicId,
        @Valid @RequestBody PrototypeDtos.UpdateReviewStatusRequest req,
        CurrentUser currentUser
    ) {
        PrototypeDtos.PrototypeResponse res = prototypeService.updateReviewStatus(publicId, req, currentUser);
        return ResponseEntity.ok(Map.of("data", res));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Map<String, Object>> archivePrototype(
        @PathVariable("id") String publicId,
        @RequestBody PrototypeDtos.ArchiveRequest req,
        CurrentUser currentUser
    ) {
        PrototypeDtos.PrototypeResponse res = prototypeService.archivePrototype(publicId, req, currentUser);
        return ResponseEntity.ok(Map.of("data", res));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePrototype(
        @PathVariable("id") String publicId,
        CurrentUser currentUser
    ) {
        prototypeService.deletePrototype(publicId, currentUser);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    private Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String clean = ifMatch.trim().replace("\"", "").replace("W/", "");
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
