package com.cassierq.api.auth.dto;

import com.cassierq.api.domain.entity.User;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, UUID storeId) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStore().getId());
    }
}
