package com.cassierq.api.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull(message = "productId wajib diisi")
        UUID productId,

        @NotNull(message = "quantity wajib diisi")
        @Min(value = 1, message = "quantity minimal 1")
        Integer quantity) {
}
