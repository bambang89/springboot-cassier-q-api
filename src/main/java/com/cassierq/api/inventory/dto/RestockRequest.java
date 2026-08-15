package com.cassierq.api.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record RestockRequest(
        @NotNull(message = "Satuan wajib dipilih")
        UUID unitId,

        @NotNull(message = "Jumlah wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah harus lebih dari 0")
        BigDecimal quantity,

        @Size(max = 500)
        String notes) {
}
