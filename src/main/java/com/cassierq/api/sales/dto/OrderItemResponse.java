package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }
}
