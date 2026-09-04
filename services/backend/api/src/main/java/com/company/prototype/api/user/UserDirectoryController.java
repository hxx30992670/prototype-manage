package com.company.prototype.api.user;

import com.company.prototype.api.security.CurrentUser;
import com.company.prototype.common.error.ApiErrorCode;
import com.company.prototype.common.error.ApiException;
import com.company.prototype.persistence.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserDirectoryController {

    public record AssignableOwner(
        String publicId,
        String username,
        String displayName
    ) {}

    private final UserRepository userRepository;

    public UserDirectoryController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/assignable-owners")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> listAssignableOwners(CurrentUser currentUser) {
        if (currentUser == null || (!currentUser.isAdmin() && !currentUser.isCreator())) {
            throw new ApiException(ApiErrorCode.ACCESS_DENIED);
        }
        List<AssignableOwner> data = userRepository.findActiveAssignableOwners().stream()
            .map(user -> new AssignableOwner(user.getPublicId(), user.getUsername(), user.getDisplayName()))
            .toList();
        return ResponseEntity.ok(Map.of("data", data));
    }
}
