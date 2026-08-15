package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.SalesTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String transactionNumber,
        UUID storeId,
        UUID cashierId,
        String cashierName,
        Instant transactionDate,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        BigDecimal paymentAmount,
        BigDecimal changeAmount,
        String status,
        String voidReason,
        List<OrderItemResponse> items,
        UUID customerId,
        BigDecimal debtAmount) {

    public static OrderResponse from(SalesTransaction transaction, List<OrderItemResponse> items) {
        return from(transaction, items, null, null);
    }

    /** customerId/debtAmount are only known at creation time (see OrderService.create) — not reconstructed on get()/list(). */
    public static OrderResponse from(SalesTransaction transaction, List<OrderItemResponse> items, UUID customerId, BigDecimal debtAmount) {
        return new OrderResponse(
                transaction.getId(),
                transaction.getTransactionNumber(),
                transaction.getStore().getId(),
                transaction.getCashier().getId(),
                transaction.getCashier().getEmployee().getFullName(),
                transaction.getTransactionDate(),
                transaction.getSubtotal(),
                transaction.getDiscountAmount(),
                transaction.getTaxAmount(),
                transaction.getGrandTotal(),
                transaction.getPaymentAmount(),
                transaction.getChangeAmount(),
                transaction.getTransactionStatus(),
                transaction.getVoidReason(),
                items,
                customerId,
                debtAmount);
    }
}
