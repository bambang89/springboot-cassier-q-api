package com.cassierq.api.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CustomerRequest(
        @NotBlank(message = "Kode pelanggan wajib diisi")
        @Size(max = 40)
        String customerCode,

        @NotBlank(message = "Nama pelanggan wajib diisi")
        @Size(max = 150)
        String name,

        @Size(max = 30)
        String phone,

        @Size(max = 255)
        String address,

        @DecimalMin(value = "0", message = "Limit kredit tidak boleh negatif")
        BigDecimal creditLimit) {
}
