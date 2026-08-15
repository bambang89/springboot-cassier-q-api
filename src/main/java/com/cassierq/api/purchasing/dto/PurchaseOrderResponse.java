package com.cassierq.api.purchasing.dto;

import com.cassierq.api.domain.entity.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String poNumber,
        UUID storeId,
        UUID supplierId,
        String supplierName,
        Instant orderDate,
        LocalDate expectedDate,
        String status,
        String notes,
        BigDecimal totalCost,
        List<PurchaseOrderItemResponse> items) {

    public static PurchaseOrderResponse from(PurchaseOrder po, List<PurchaseOrderItemResponse> items) {
        BigDecimal total = items.stream().map(PurchaseOrderItemResponse::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getStore().getId(),
                po.getSupplier().getId(),
                po.getSupplier().getSupplierName(),
                po.getOrderDate(),
                po.getExpectedDate(),
                po.getStatus(),
                po.getNotes(),
                total,
                items);
    }
}
