package com.company.prototype.api.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> listCategories() {
        List<CatalogService.CategoryDto> list = catalogService.listCategories();
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@RequestBody CatalogService.SaveCategoryRequest req) {
        CatalogService.CategoryDto dto = catalogService.createCategory(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", dto));
    }

    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
        @PathVariable("id") String publicId,
        @RequestBody CatalogService.SaveCategoryRequest req
    ) {
        CatalogService.CategoryDto dto = catalogService.updateCategory(publicId, req);
        return ResponseEntity.ok(Map.of("data", dto));
    }

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable("id") String publicId) {
        catalogService.deleteCategory(publicId);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    @GetMapping("/tags")
    public ResponseEntity<Map<String, Object>> listTags() {
        List<CatalogService.TagDto> list = catalogService.listTags();
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATOR')")
    public ResponseEntity<Map<String, Object>> createTag(@RequestBody CatalogService.SaveTagRequest req) {
        CatalogService.TagDto dto = catalogService.createTag(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", dto));
    }

    @PutMapping("/admin/tags/{id}")
    public ResponseEntity<Map<String, Object>> updateTag(
        @PathVariable("id") String publicId,
        @RequestBody CatalogService.SaveTagRequest req
    ) {
        CatalogService.TagDto dto = catalogService.updateTag(publicId, req);
        return ResponseEntity.ok(Map.of("data", dto));
    }

    @DeleteMapping("/admin/tags/{id}")
    public ResponseEntity<Map<String, Object>> deleteTag(@PathVariable("id") String publicId) {
        catalogService.deleteTag(publicId);
        return ResponseEntity.ok(Map.of("data", "success"));
    }
}
