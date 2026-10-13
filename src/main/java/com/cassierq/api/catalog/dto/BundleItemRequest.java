package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record BundleItemRequest(
        @NotNull(message = "Produk isi paket wajib dipilih")
        UUID productId,

        @NotNull(message = "Satuan isi paket wajib dipilih")
        UUID unitId,

        @NotNull(message = "Jumlah isi paket wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah isi paket harus lebih dari 0")
        BigDecimal quantity) {
}
