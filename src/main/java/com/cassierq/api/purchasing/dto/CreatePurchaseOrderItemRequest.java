package com.cassierq.api.purchasing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseOrderItemRequest(
        @NotNull(message = "Produk wajib dipilih")
        UUID productId,

        @NotNull(message = "Satuan wajib dipilih")
        UUID unitId,

        @NotNull(message = "Jumlah wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0")
        BigDecimal quantity,

        @NotNull(message = "Harga beli wajib diisi")
        @DecimalMin(value = "0", message = "Harga beli tidak boleh negatif")
        BigDecimal unitCost) {
}
