package com.cassierq.api.customer.dto;

import com.cassierq.api.domain.entity.CustomerLedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        String entryType,
        BigDecimal amount,
        UUID salesTransactionId,
        String salesTransactionNumber,
        String notes,
        String createdByName,
        Instant createdAt) {

    public static LedgerEntryResponse from(CustomerLedgerEntry entry) {
        var transaction = entry.getSalesTransaction();
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getAmount(),
                transaction != null ? transaction.getId() : null,
                transaction != null ? transaction.getTransactionNumber() : null,
                entry.getNotes(),
                entry.getCreatedBy().getEmployee().getFullName(),
                entry.getCreatedAt());
    }
}
