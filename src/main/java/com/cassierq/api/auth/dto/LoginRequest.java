package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "Username wajib diisi")
        String username,

        @NotBlank(message = "Kata sandi wajib diisi")
        String password,

        @NotBlank(message = "Tipe device wajib diisi")
        @Pattern(regexp = "ANDROID|IOS|WEB", message = "Tipe device harus ANDROID, IOS, atau WEB")
        String deviceType) {
}
