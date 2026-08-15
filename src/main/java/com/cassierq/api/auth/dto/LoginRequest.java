package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username wajib diisi")
        String username,

        @NotBlank(message = "Kata sandi wajib diisi")
        String password) {
}
