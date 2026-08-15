package com.cassierq.api.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything a client needs to render/print a receipt — not tied to any specific printer format (ESC/POS, PDF, ...). */
public record ReceiptResponse(
        UUID orderId,
        String transactionNumber,
        Instant transactionDate,
        String status,
        String storeName,
        String storeAddress,
        String storePhone,
        String cashierName,
        List<ReceiptItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal grandTotal,
        String paymentMethod,
        BigDecimal paymentAmount,
        BigDecimal changeAmount,
        String customerName,
        BigDecimal debtAmount) {
}
