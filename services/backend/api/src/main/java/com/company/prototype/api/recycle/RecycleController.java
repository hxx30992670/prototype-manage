package com.company.prototype.api.recycle;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.persistence.cleanup.CleanupTaskEntity;
import com.company.prototype.persistence.prototype.PrototypeEntity;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RecycleController {

    private final RecycleService recycleService;

    public RecycleController(RecycleService recycleService) {
        this.recycleService = recycleService;
    }

    @GetMapping("/admin/recycle-bin")
    public ResponseEntity<Map<String, Object>> listRecycled(
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        CurrentUser currentUser
    ) {
        Page<PrototypeEntity> result = recycleService.listRecycled(currentUser, page, pageSize);
        return ResponseEntity.ok(Map.of(
            "data", result.getContent().stream().map(RecycleView::from).toList(),
            "pagination", Map.of(
                "page", result.getNumber() + 1,
                "pageSize", result.getSize(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages()
            )
        ));
    }

    @PostMapping("/admin/prototypes/{id}/restore")
    public ResponseEntity<Map<String, Object>> restore(
        @PathVariable("id") String publicId,
        CurrentUser currentUser
    ) {
        recycleService.restorePrototype(publicId, currentUser);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    @DeleteMapping("/admin/prototypes/{id}/purge")
    public ResponseEntity<Map<String, Object>> purge(
        @PathVariable("id") String publicId,
        CurrentUser currentUser
    ) {
        CleanupTaskEntity task = recycleService.purgePrototype(publicId, currentUser);
        return ResponseEntity.ok(Map.of("data", Map.of("taskId", task.getId())));
    }
}
