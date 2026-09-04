package com.company.prototype.api.spec;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/prototypes/{prototypeId}/spec")
public class PrototypeSpecController {

    private final PrototypeSpecService specService;

    public PrototypeSpecController(PrototypeSpecService specService) {
        this.specService = specService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSpec(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId
    ) {
        var resp = specService.getSpec(user, prototypeId);
        return ResponseEntity.ok(Map.of("data", resp));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSpec(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable("prototypeId") String prototypeId,
        @Valid @RequestBody SpecDtos.UpdateSpecRequest req
    ) {
        var resp = specService.updateSpec(user, prototypeId, req);
        return ResponseEntity.ok(Map.of("data", resp));
    }
}
