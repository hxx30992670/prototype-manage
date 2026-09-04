package com.company.prototype.api.user;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listUsers(Pageable pageable) {
        Page<UserAdminDtos.UserDetailResponse> page = userAdminService.listUsers(pageable);
        return ResponseEntity.ok(Map.of(
            "data", page.getContent(),
            "pagination", Map.of(
                "page", page.getNumber() + 1,
                "pageSize", page.getSize(),
                "total", page.getTotalElements(),
                "totalPages", page.getTotalPages()
            )
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody UserAdminDtos.CreateUserRequest req) {
        UserAdminDtos.UserDetailResponse user = userAdminService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
        @PathVariable("id") String publicId,
        @Valid @RequestBody UserAdminDtos.UpdateUserRequest req
    ) {
        UserAdminDtos.UserDetailResponse user = userAdminService.updateUser(publicId, req);
        return ResponseEntity.ok(Map.of("data", user));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
        @PathVariable("id") String publicId,
        @Valid @RequestBody UserAdminDtos.ResetPasswordRequest req
    ) {
        userAdminService.resetPassword(publicId, req);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(
        @PathVariable("id") String publicId,
        @Valid @RequestBody UserAdminDtos.UpdateRolesRequest req
    ) {
        UserAdminDtos.UserDetailResponse user = userAdminService.updateRoles(publicId, req);
        return ResponseEntity.ok(Map.of("data", user));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
        @PathVariable("id") String publicId,
        @Valid @RequestBody UserAdminDtos.UpdateStatusRequest req
    ) {
        UserAdminDtos.UserDetailResponse user = userAdminService.updateStatus(publicId, req);
        return ResponseEntity.ok(Map.of("data", user));
    }
}
