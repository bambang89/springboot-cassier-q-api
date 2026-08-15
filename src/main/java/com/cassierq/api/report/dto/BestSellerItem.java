package com.cassierq.api.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BestSellerItem(UUID productId, String productName, BigDecimal totalQuantity, BigDecimal totalRevenue) {
}
