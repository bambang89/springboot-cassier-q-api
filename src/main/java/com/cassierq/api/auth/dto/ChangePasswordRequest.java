package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Kata sandi saat ini wajib diisi")
        String currentPassword,

        @NotBlank(message = "Kata sandi baru wajib diisi")
        @Size(min = 8, message = "Kata sandi minimal 8 karakter")
        String newPassword) {
}
