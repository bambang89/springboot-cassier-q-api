package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Nama kategori wajib diisi")
        @Size(max = 100)
        String name) {
}
