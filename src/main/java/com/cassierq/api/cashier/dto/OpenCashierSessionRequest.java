package com.cassierq.api.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OpenCashierSessionRequest(
        @NotNull(message = "Modal awal wajib diisi")
        @DecimalMin(value = "0", message = "Modal awal tidak boleh negatif")
        BigDecimal openingCash) {
}
