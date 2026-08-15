package com.cassierq.api.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotEmpty(message = "Item transaksi wajib diisi")
        @Valid
        List<CreateOrderItemRequest> items,

        @NotNull(message = "Metode pembayaran wajib diisi")
        @Pattern(regexp = "CASH|CREDIT_CARD|DEBIT|TRANSFER|QRIS", message = "Metode pembayaran tidak valid")
        String paymentMethod,

        @NotNull(message = "Jumlah pembayaran wajib diisi")
        @DecimalMin(value = "0", message = "Jumlah pembayaran tidak boleh negatif")
        BigDecimal paymentAmount,

        @DecimalMin(value = "0", message = "Diskon tidak boleh negatif")
        BigDecimal discountAmount,

        @DecimalMin(value = "0", message = "Pajak tidak boleh negatif")
        BigDecimal taxAmount,

        // Optional. When set and paymentAmount < total, the shortfall is
        // recorded as a debt for this customer instead of being rejected —
        // see CustomerService.recordDebtForSale. Omit entirely for the
        // original cash/full-payment-only behavior.
        UUID customerId) {
}
