package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(
        @NotBlank(message = "Kode kategori wajib diisi")
        @Size(max = 20)
        String categoryCode,

        @NotBlank(message = "Nama kategori wajib diisi")
        @Size(max = 100)
        String categoryName,

        UUID parentCategoryId) {
}
