package com.cassierq.api.employee.dto;

import com.cassierq.api.domain.entity.Role;
import java.util.UUID;

public record RoleResponse(UUID id, String roleCode, String roleName, String description) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription());
    }
}
