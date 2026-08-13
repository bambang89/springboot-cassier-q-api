package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Nama toko wajib diisi")
        @Size(max = 150)
        String storeName,

        @NotBlank(message = "Nama wajib diisi")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Kata sandi wajib diisi")
        @Size(min = 8, message = "Kata sandi minimal 8 karakter")
        String password) {
}
