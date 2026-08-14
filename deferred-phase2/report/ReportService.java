package com.cassierq.api.report;

import com.cassierq.api.domain.entity.OrderStatus;
import com.cassierq.api.domain.repository.OrderItemRepository;
import com.cassierq.api.domain.repository.OrderRepository;
import com.cassierq.api.report.dto.BestSellerItem;
import com.cassierq.api.report.dto.SalesSummaryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int BEST_SELLERS_LIMIT = 5;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(UUID storeId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        // Exclusive upper bound: the day after `to`, so `to` itself is fully included.
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        var grossSales = orderRepository.sumTotalByStoreAndStatusAndCreatedAtBetween(
                storeId, OrderStatus.PAID, fromInstant, toInstant);
        var paidCount = orderRepository.countByStoreAndStatusAndCreatedAtBetween(
                storeId, OrderStatus.PAID, fromInstant, toInstant);
        var cancelledCount = orderRepository.countByStoreAndStatusAndCreatedAtBetween(
                storeId, OrderStatus.CANCELLED, fromInstant, toInstant);

        var bestSellers = orderItemRepository
                .findBestSellers(storeId, fromInstant, toInstant, PageRequest.of(0, BEST_SELLERS_LIMIT))
                .stream()
                .map(BestSellerItem::from)
                .toList();

        return new SalesSummaryResponse(from, to, grossSales, paidCount, cancelledCount, bestSellers);
    }
}
