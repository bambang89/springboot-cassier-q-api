package com.cassierq.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Registers an additional unit a product can be bought/sold in — the write-side counterpart of {@code GET /products/{id}/convert}. */
public record RegisterProductUnitRequest(
        @NotNull(message = "Satuan wajib dipilih")
        UUID unitId,

        @NotNull(message = "Rasio konversi wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Rasio konversi harus lebih dari 0")
        BigDecimal conversionToBase,

        // Default true when omitted — a newly registered unit is usable for
        // both buying and selling unless the caller says otherwise.
        Boolean purchaseUnit,
        Boolean saleUnit) {
}
