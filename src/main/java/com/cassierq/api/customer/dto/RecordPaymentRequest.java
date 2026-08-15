package com.cassierq.api.customer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull(message = "Jumlah bayar wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah bayar harus lebih dari 0")
        BigDecimal amount,

        @Size(max = 500)
        String notes) {
}
