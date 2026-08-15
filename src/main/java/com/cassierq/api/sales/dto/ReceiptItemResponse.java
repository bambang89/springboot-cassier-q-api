package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.SalesTransactionItem;
import java.math.BigDecimal;

public record ReceiptItemResponse(
        String productName,
        BigDecimal quantity,
        String unitName,
        BigDecimal unitPrice,
        BigDecimal subtotal) {

    public static ReceiptItemResponse from(SalesTransactionItem item) {
        return new ReceiptItemResponse(
                item.getProduct().getProductName(),
                item.getQuantity(),
                item.getUnit().getUnitName(),
                item.getUnitPrice(),
                item.getSubtotal());
    }
}
