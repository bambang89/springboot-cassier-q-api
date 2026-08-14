package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        String paymentMethod,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        UUID customerId,
        Instant createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getPaymentMethod().name(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getTotal(),
                order.getCustomer() != null ? order.getCustomer().getId() : null,
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
