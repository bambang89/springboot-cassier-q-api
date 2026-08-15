package com.cassierq.api.purchasing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "Kode supplier wajib diisi")
        @Size(max = 20)
        String supplierCode,

        @NotBlank(message = "Nama supplier wajib diisi")
        @Size(max = 150)
        String supplierName,

        @Size(max = 100)
        String contactPerson,

        @Size(max = 30)
        String phone,

        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @Size(max = 255)
        String address) {
}
