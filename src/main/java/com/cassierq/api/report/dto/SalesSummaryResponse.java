package com.cassierq.api.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesSummaryResponse(
        LocalDate from,
        LocalDate to,
        long orderCount,
        BigDecimal grossSales,
        List<BestSellerItem> topSellers) {
}
