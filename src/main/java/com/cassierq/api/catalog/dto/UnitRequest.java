package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitRequest(
        @NotBlank(message = "Kode satuan wajib diisi")
        @Size(max = 20)
        String unitCode,

        @NotBlank(message = "Nama satuan wajib diisi")
        @Size(max = 50)
        String unitName) {
}
