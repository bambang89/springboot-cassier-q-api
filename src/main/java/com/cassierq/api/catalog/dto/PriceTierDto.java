package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * One wholesale price step.
 *
 * @param minQuantity minimum total quantity in the product's base unit (more than 1)
 * @param unitPrice   price per base unit from that quantity
 */
public record PriceTierDto(
        @NotNull(message = "Minimal jumlah wajib diisi")
        @DecimalMin(value = "1", inclusive = false, message = "Minimal jumlah harus lebih dari 1")
        BigDecimal minQuantity,

        @NotNull(message = "Harga grosir wajib diisi")
        @DecimalMin(value = "0", message = "Harga grosir tidak boleh negatif")
        BigDecimal unitPrice) {
}
