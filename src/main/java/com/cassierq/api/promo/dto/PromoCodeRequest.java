package com.cassierq.api.promo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeRequest(
        @NotBlank(message = "Kode promo wajib diisi")
        @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "Kode promo 3–30 huruf/angka (boleh - dan _)")
        String code,

        @NotBlank(message = "Nama promo wajib diisi")
        @Size(max = 120)
        String name,

        @NotNull(message = "Jenis diskon wajib dipilih")
        @Pattern(regexp = "PERCENT|AMOUNT", message = "Jenis diskon harus PERCENT atau AMOUNT")
        String discountType,

        @NotNull(message = "Nilai diskon wajib diisi")
        @DecimalMin(value = "0.01", message = "Nilai diskon harus lebih dari 0")
        BigDecimal discountValue,

        @DecimalMin(value = "0", message = "Maksimal diskon tidak boleh negatif")
        BigDecimal maxDiscountAmount,

        @DecimalMin(value = "0", message = "Minimal belanja tidak boleh negatif")
        BigDecimal minPurchaseAmount,

        Instant startsAt,
        Instant endsAt,

        @Min(value = 1, message = "Batas pemakaian minimal 1")
        Integer usageLimit,

        Boolean active) {
}
