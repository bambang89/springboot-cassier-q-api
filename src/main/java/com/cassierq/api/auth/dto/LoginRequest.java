package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email wajib diisi")
        String email,

        @NotBlank(message = "Kata sandi wajib diisi")
        String password) {
}
