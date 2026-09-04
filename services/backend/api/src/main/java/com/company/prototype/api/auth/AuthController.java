package com.company.prototype.api.auth;

import com.company.prototype.api.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
        @Valid @RequestBody AuthDtos.LoginRequest req,
        HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response
    ) {
        AuthDtos.UserProfileResponse profile = authService.login(req, request, response);
        return ResponseEntity.ok(Map.of("data", profile));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
        @AuthenticationPrincipal CurrentUser currentUser,
        HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response
    ) {
        authService.logout(currentUser, request, response);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
        @AuthenticationPrincipal CurrentUser currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        AuthDtos.UserProfileResponse profile = authService.getCurrentUserProfile(currentUser);
        return ResponseEntity.ok(Map.of("data", profile));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, Object>> csrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            return ResponseEntity.ok(Map.of("data", new AuthDtos.CsrfResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
            )));
        }
        return ResponseEntity.ok(Map.of("data", Map.of("token", "dummy-csrf")));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
        @AuthenticationPrincipal CurrentUser currentUser,
        @Valid @RequestBody AuthDtos.ChangePasswordRequest req,
        HttpServletRequest request
    ) {
        authService.changePassword(currentUser, req, request);
        return ResponseEntity.ok(Map.of("data", "success"));
    }

    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> captcha() {
        return ResponseEntity.ok(Map.of("data", authService.createCaptcha()));
    }
}
