package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "SKU wajib diisi")
        @Size(max = 30)
        String sku,

        @Size(max = 50)
        String barcode,

        @NotBlank(message = "Nama produk wajib diisi")
        @Size(max = 200)
        String productName,

        @NotNull(message = "Kategori wajib dipilih")
        UUID categoryId,

        @Size(max = 100)
        String brand,

        @Size(max = 500)
        String description,

        @NotNull(message = "Satuan dasar wajib dipilih")
        UUID baseUnitId,

        @NotNull(message = "Harga jual wajib diisi")
        @DecimalMin(value = "0", message = "Harga jual tidak boleh negatif")
        BigDecimal sellingPrice,

        @DecimalMin(value = "0", message = "Harga modal tidak boleh negatif")
        BigDecimal costPrice) {
}
