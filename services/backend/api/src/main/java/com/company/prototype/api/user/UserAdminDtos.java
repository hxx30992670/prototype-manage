package com.company.prototype.api.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public class UserAdminDtos {

    public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度需在2-50之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度需在6-100之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        String displayName,

        String department,

        @NotEmpty(message = "至少选择一个角色")
        Set<String> roles
    ) {}

    public record UpdateUserRequest(
        @NotBlank(message = "显示名称不能为空")
        String displayName,
        String department,
        Set<String> roles
    ) {}

    public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度需在6-100之间")
        String newPassword
    ) {}

    public record UpdateRolesRequest(
        @NotEmpty(message = "至少选择一个角色")
        Set<String> roles
    ) {}

    public record UpdateStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
    ) {}

    public record UserDetailResponse(
        String publicId,
        String username,
        String displayName,
        String department,
        String status,
        Set<String> roles,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant createdAt,
        boolean protectedAccount
    ) {}
}
