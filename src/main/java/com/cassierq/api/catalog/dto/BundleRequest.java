package com.cassierq.api.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BundleRequest(
        @NotBlank(message = "SKU wajib diisi")
        @Size(max = 30)
        String sku,

        @Size(max = 50)
        String barcode,

        @NotBlank(message = "Nama paket wajib diisi")
        @Size(max = 200)
        String productName,

        @NotNull(message = "Kategori wajib dipilih")
        UUID categoryId,

        @Size(max = 500)
        String description,

        @Size(max = 500)
        String imageUrl,

        @NotNull(message = "Harga paket wajib diisi")
        @DecimalMin(value = "0", message = "Harga paket tidak boleh negatif")
        BigDecimal sellingPrice,

        @NotEmpty(message = "Paket harus berisi minimal satu produk")
        @Valid
        List<BundleItemRequest> items) {
}
