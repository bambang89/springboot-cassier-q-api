package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.SalesTransactionItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        UUID unitId,
        String unitName,
        BigDecimal quantity,
        BigDecimal quantityBaseUnit,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal subtotal) {

    public static OrderItemResponse from(SalesTransactionItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getProductName(),
                item.getUnit().getId(),
                item.getUnit().getUnitName(),
                item.getQuantity(),
                item.getQuantityBaseUnit(),
                item.getUnitPrice(),
                item.getDiscount(),
                item.getSubtotal());
    }
}
