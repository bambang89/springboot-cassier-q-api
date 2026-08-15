package com.cassierq.api.report;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.domain.repository.SalesTransactionItemRepository;
import com.cassierq.api.domain.repository.SalesTransactionRepository;
import com.cassierq.api.report.dto.BestSellerItem;
import com.cassierq.api.report.dto.SalesSummaryResponse;
import com.cassierq.api.security.AppUserPrincipal;
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

    private static final int TOP_SELLERS_LIMIT = 5;

    private final SalesTransactionRepository transactionRepository;
    private final SalesTransactionItemRepository itemRepository;

    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(AppUserPrincipal principal, LocalDate from, LocalDate to) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }

        LocalDate effectiveTo = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(6);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("Tanggal 'from' tidak boleh setelah 'to'");
        }

        Instant fromInstant = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long orderCount = transactionRepository.countPaid(storeId, fromInstant, toInstant);
        var grossSales = transactionRepository.sumGrandTotalPaid(storeId, fromInstant, toInstant);

        var topSellers = itemRepository.findBestSellers(storeId, fromInstant, toInstant, PageRequest.of(0, TOP_SELLERS_LIMIT))
                .stream()
                .map(row -> new BestSellerItem(row.getProductId(), row.getProductName(), row.getTotalQuantity(), row.getTotalRevenue()))
                .toList();

        return new SalesSummaryResponse(effectiveFrom, effectiveTo, orderCount, grossSales, topSellers);
    }
}
