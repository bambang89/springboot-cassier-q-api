package com.cassierq.api.promo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ValidatePromoRequest(
        @NotBlank(message = "Kode promo wajib diisi")
        String code,

        @NotNull(message = "Subtotal wajib diisi")
        @DecimalMin(value = "0", message = "Subtotal tidak boleh negatif")
        BigDecimal subtotal) {
}
