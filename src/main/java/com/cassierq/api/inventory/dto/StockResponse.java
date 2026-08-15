package com.cassierq.api.inventory.dto;

import com.cassierq.api.domain.entity.Inventory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockResponse(
        UUID productId,
        String productName,
        UUID storeId,
        BigDecimal quantityBaseUnit,
        BigDecimal minimumStock,
        BigDecimal maximumStock,
        Instant updatedAt) {

    public static StockResponse from(Inventory inventory) {
        return new StockResponse(
                inventory.getProduct().getId(),
                inventory.getProduct().getProductName(),
                inventory.getStore().getId(),
                inventory.getQuantityBaseUnit(),
                inventory.getMinimumStock(),
                inventory.getMaximumStock(),
                inventory.getUpdatedAt());
    }
}
