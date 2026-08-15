package com.cassierq.api.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edits an existing employee's profile + role. Username/password aren't changed here — see /auth/change-password. */
public record UpdateEmployeeRequest(
        @NotBlank(message = "Nama wajib diisi")
        @Size(max = 150)
        String name,

        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        @NotBlank(message = "Role wajib diisi")
        @Pattern(regexp = "KEPALA_TOKO|PRODUCT|GUDANG|KASIR", message = "Role harus KEPALA_TOKO, PRODUCT, GUDANG, atau KASIR")
        String roleCode) {
}
