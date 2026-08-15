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
        List<OrderItemResponse> items) {

    public static OrderResponse from(SalesTransaction transaction, List<OrderItemResponse> items) {
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
                items);
    }
}
