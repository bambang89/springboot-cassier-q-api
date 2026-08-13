package com.cassierq.api.report.dto;

import com.cassierq.api.domain.repository.OrderItemRepository.BestSellerRow;
import java.math.BigDecimal;

public record BestSellerItem(String productName, long totalQuantity, BigDecimal totalRevenue) {

    public static BestSellerItem from(BestSellerRow row) {
        return new BestSellerItem(row.getProductName(), row.getTotalQuantity(), row.getTotalRevenue());
    }
}
