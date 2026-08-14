package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates a new store plus its KEPALA_TOKO (store head) account in one
 * transaction — the closest equivalent, in the real RBAC schema, of the
 * original "register creates a store + its owner" flow.
 */
public record RegisterRequest(
        @NotBlank(message = "Kode toko wajib diisi")
        @Size(max = 40)
        String storeCode,

        @NotBlank(message = "Nama toko wajib diisi")
        @Size(max = 300)
        String storeName,

        @NotBlank(message = "Username wajib diisi")
        @Size(max = 100)
        String username,

        @NotBlank(message = "Nama wajib diisi")
        @Size(max = 300)
        String name,

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Kata sandi wajib diisi")
        @Size(min = 8, message = "Kata sandi minimal 8 karakter")
        String password) {
}
