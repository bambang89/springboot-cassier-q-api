package com.cassierq.api.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull(message = "Produk wajib dipilih")
        UUID productId,

        @NotNull(message = "Satuan wajib dipilih")
        UUID unitId,

        @NotNull(message = "Jumlah wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0")
        BigDecimal quantity) {
}
