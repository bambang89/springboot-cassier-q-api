package com.cassierq.api.cashier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CloseCashierSessionRequest(
        @NotNull(message = "Uang aktual wajib diisi")
        @DecimalMin(value = "0", message = "Uang aktual tidak boleh negatif")
        BigDecimal actualCash,

        @Size(max = 500)
        String notes) {
}
