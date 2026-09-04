package com.company.prototype.api.security;

import java.util.Objects;
import java.util.Set;

public record CurrentUser(
    Long id,
    String publicId,
    String username,
    String displayName,
    Set<String> roles,
    boolean mustChangePassword
) {
    public boolean hasRole(String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isCreator() {
        return hasRole("CREATOR");
    }

    /**
     * SessionRegistry 按 principal.equals 匹配会话。会话里的角色/显示名可能已过期，
     * 禁用用户时必须只按用户身份匹配，避免注销遗漏。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CurrentUser other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
