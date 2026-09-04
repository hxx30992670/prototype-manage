package com.company.prototype.api.audit;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> query(
        @RequestParam(value = "actorPublicId", required = false) String actorPublicId,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "targetType", required = false) String targetType,
        @RequestParam(value = "targetId", required = false) String targetId,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "traceId", required = false) String traceId,
        @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Page<AuditLogView> pageResult = auditQueryService.query(
            actorPublicId, action, targetType, targetId, result, traceId, from, to, page, pageSize)
            .map(AuditLogView::from);
        return ResponseEntity.ok(Map.of(
            "data", pageResult.getContent(),
            "pagination", Map.of(
                "page", pageResult.getNumber() + 1,
                "pageSize", pageResult.getSize(),
                "total", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages()
            )
        ));
    }
}
