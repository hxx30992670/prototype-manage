package com.company.prototype.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public class AuthDtos {

    public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,

        @NotBlank(message = "密码不能为空")
        String password,

        String captchaId,
        String captchaCode
    ) {}

    public record UserProfileResponse(
        String publicId,
        String username,
        String displayName,
        String department,
        Set<String> roles,
        boolean mustChangePassword
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "新密码长度需在6-100字符之间")
        String newPassword
    ) {}

    public record CsrfResponse(
        String token,
        String headerName,
        String parameterName
    ) {}
}
