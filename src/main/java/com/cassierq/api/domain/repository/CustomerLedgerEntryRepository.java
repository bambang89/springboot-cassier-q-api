package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.CustomerLedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerLedgerEntryRepository extends JpaRepository<CustomerLedgerEntry, UUID> {

    List<CustomerLedgerEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    // For receipts — at most one DEBT entry per sale in practice (see OrderService.create).
    Optional<CustomerLedgerEntry> findFirstBySalesTransactionIdAndEntryType(UUID salesTransactionId, String entryType);

    // Outstanding balance = sum(DEBT) - sum(PAYMENT). Positive = customer owes the store.
    @Query("""
            select coalesce(sum(case when e.entryType = 'DEBT' then e.amount else -e.amount end), 0)
            from CustomerLedgerEntry e
            where e.customer.id = :customerId
            """)
    BigDecimal balanceOf(UUID customerId);
}
