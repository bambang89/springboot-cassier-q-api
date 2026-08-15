package com.cassierq.api.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Adds a new employee + login account to the caller's own store — unlike
 * {@code /api/v1/auth/register}, which always creates a brand new store.
 */
public record CreateEmployeeRequest(
        @NotBlank(message = "Nama wajib diisi")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Username wajib diisi")
        @Size(max = 50)
        String username,

        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        @NotBlank(message = "Kata sandi awal wajib diisi")
        @Size(min = 8, message = "Kata sandi minimal 8 karakter")
        String password,

        @NotBlank(message = "Role wajib diisi")
        @Pattern(regexp = "KEPALA_TOKO|PRODUCT|GUDANG|KASIR", message = "Role harus KEPALA_TOKO, PRODUCT, GUDANG, atau KASIR")
        String roleCode) {
}
