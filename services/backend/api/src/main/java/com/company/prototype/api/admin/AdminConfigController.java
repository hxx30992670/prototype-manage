package com.company.prototype.api.admin;

import com.company.prototype.api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminConfigController {

    private final AdminConfigService configService;
    private final TagMergeService tagMergeService;

    public AdminConfigController(AdminConfigService configService, TagMergeService tagMergeService) {
        this.configService = configService;
        this.tagMergeService = tagMergeService;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> listConfig() {
        List<AdminConfigDtos.ConfigItemResponse> items = configService.listAll();
        return ResponseEntity.ok(Map.of("data", items));
    }

    @PutMapping("/config/{key}")
    public ResponseEntity<Map<String, Object>> updateConfig(
        @PathVariable("key") String key,
        @Valid @RequestBody AdminConfigDtos.UpdateConfigRequest req,
        CurrentUser currentUser
    ) {
        AdminConfigDtos.ConfigItemResponse item = configService.update(key, req.value(), currentUser.id());
        return ResponseEntity.ok(Map.of("data", item));
    }

    @PostMapping("/tags/{sourceTagName}/merge")
    public ResponseEntity<Map<String, Object>> mergeTags(
        @PathVariable("sourceTagName") String sourceTagName,
        @Valid @RequestBody AdminConfigDtos.TagMergeRequest req,
        CurrentUser currentUser
    ) {
        AdminConfigDtos.TagMergeResponse result = tagMergeService.mergeTags(sourceTagName, req.targetTagName(), currentUser);
        return ResponseEntity.ok(Map.of("data", result));
    }
}
