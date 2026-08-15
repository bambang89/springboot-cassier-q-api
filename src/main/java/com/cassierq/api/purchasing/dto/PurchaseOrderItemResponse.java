package com.cassierq.api.purchasing.dto;

import com.cassierq.api.domain.entity.PurchaseOrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        UUID unitId,
        String unitName,
        BigDecimal quantity,
        BigDecimal quantityBaseUnit,
        BigDecimal unitCost,
        BigDecimal receivedQuantityBaseUnit,
        BigDecimal subtotal) {

    public static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getProductName(),
                item.getUnit().getId(),
                item.getUnit().getUnitName(),
                item.getQuantity(),
                item.getQuantityBaseUnit(),
                item.getUnitCost(),
                item.getReceivedQuantityBaseUnit(),
                item.getSubtotal());
    }
}
