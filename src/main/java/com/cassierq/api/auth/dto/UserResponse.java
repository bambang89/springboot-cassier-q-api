package com.cassierq.api.auth.dto;

import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String name,
        String email,
        boolean superadmin,
        List<RoleAssignment> roles) {

    public record RoleAssignment(String roleCode, String roleName, UUID storeId, String storeName) {
    }

    public static UserResponse from(User user, List<UserRole> userRoles) {
        List<RoleAssignment> roles = userRoles.stream()
                .map(ur -> new RoleAssignment(
                        ur.getRole().getRoleCode(),
                        ur.getRole().getRoleName(),
                        ur.getStore() != null ? ur.getStore().getId() : null,
                        ur.getStore() != null ? ur.getStore().getStoreName() : null))
                .toList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmployee().getFullName(),
                user.getEmail(),
                user.isSuperadmin(),
                roles);
    }
}
