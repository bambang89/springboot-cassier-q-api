package com.cassierq.api.purchasing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveItemRequest(
        @NotNull(message = "Item PO wajib diisi")
        UUID purchaseOrderItemId,

        // In the item's own ordered unit (same as CreatePurchaseOrderItemRequest.quantity), not base unit.
        @NotNull(message = "Jumlah diterima wajib diisi")
        @DecimalMin(value = "0", inclusive = false, message = "Jumlah diterima harus lebih dari 0")
        BigDecimal receivedQuantity) {
}
