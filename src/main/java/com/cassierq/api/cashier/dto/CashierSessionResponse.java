package com.cassierq.api.cashier.dto;

import com.cassierq.api.domain.entity.CashierSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashierSessionResponse(
        UUID id,
        UUID storeId,
        UUID cashierId,
        String cashierName,
        Instant openedAt,
        Instant closedAt,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal cashDifference,
        String status,
        String notes) {

    public static CashierSessionResponse from(CashierSession session) {
        return new CashierSessionResponse(
                session.getId(),
                session.getStore().getId(),
                session.getCashier().getId(),
                session.getCashier().getEmployee().getFullName(),
                session.getOpenedAt(),
                session.getClosedAt(),
                session.getOpeningCash(),
                session.getExpectedCash(),
                session.getActualCash(),
                session.getCashDifference(),
                session.getStatus(),
                session.getNotes());
    }
}
