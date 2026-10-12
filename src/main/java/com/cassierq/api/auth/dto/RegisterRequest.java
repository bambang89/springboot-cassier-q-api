package com.cassierq.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Kode toko wajib diisi")
        @Size(max = 20)
        String storeCode,

        @NotBlank(message = "Nama toko wajib diisi")
        @Size(max = 150)
        String storeName,

        @NotBlank(message = "Username wajib diisi")
        @Size(max = 50)
        String username,

        @NotBlank(message = "Nama wajib diisi")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Kata sandi wajib diisi")
        @Size(min = 8, message = "Kata sandi minimal 8 karakter")
        String password,

        // Opsional — profil toko bisa dilengkapi belakangan di menu Outlet.
        @Size(max = 255)
        String address,

        @Size(max = 100)
        String city,

        @Size(max = 30)
        String phone) {
}
