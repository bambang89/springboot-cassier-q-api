package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token wajib diisi")
        String refreshToken) {
}
