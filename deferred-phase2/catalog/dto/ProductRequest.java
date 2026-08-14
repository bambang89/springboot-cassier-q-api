package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "SKU wajib diisi")
        @Size(max = 64)
        String sku,

        @Size(max = 64)
        String barcode,

        @NotBlank(message = "Nama produk wajib diisi")
        @Size(max = 150)
        String name,

        @NotNull(message = "Harga wajib diisi")
        @DecimalMin(value = "0", message = "Harga tidak boleh negatif")
        BigDecimal price,

        @NotNull(message = "Stok wajib diisi")
        @Min(value = 0, message = "Stok tidak boleh negatif")
        Integer stock,

        UUID categoryId,

        String imageUrl) {
}
