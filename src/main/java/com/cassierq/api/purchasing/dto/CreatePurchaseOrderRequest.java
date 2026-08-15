package com.cassierq.api.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull(message = "Supplier wajib dipilih")
        UUID supplierId,

        LocalDate expectedDate,

        @Size(max = 500)
        String notes,

        @NotEmpty(message = "Item pembelian wajib diisi")
        @Valid
        List<CreatePurchaseOrderItemRequest> items) {
}
