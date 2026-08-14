package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Username wajib diisi")
        String username) {
}
