package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshRequest(
        @NotBlank(message = "Refresh token wajib diisi")
        String refreshToken,

        @Pattern(regexp = "ANDROID|IOS|WEB", message = "Tipe device harus ANDROID, IOS, atau WEB")
        String deviceType) {
}
