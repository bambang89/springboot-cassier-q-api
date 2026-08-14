package com.cassierq.api.sales.dto;

import com.cassierq.api.domain.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        UUID customerId,

        @NotNull(message = "paymentMethod wajib diisi")
        PaymentMethod paymentMethod,

        @DecimalMin(value = "0", message = "discount tidak boleh negatif")
        BigDecimal discount,

        @NotEmpty(message = "Order harus punya minimal 1 item")
        @Valid
        List<CreateOrderItemRequest> items) {
}
