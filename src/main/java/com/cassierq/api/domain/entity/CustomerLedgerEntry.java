package com.cassierq.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per debt (customer bought on credit) or payment (customer paid
 * down their tab) — {@code entryType} is {@code DEBT} or {@code PAYMENT}.
 * {@link Customer#getCreditLimit()}-checked balance is the sum of DEBT minus
 * PAYMENT, computed on read (see {@code CustomerLedgerEntryRepository}), not
 * stored here. Only has {@code created_at} — entries are never edited.
 */
@Entity
@Table(name = "customer_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    // Set when a DEBT entry came from a credit sale; null for a manual debt
    // note or any PAYMENT entry.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_transaction_id")
    private SalesTransaction salesTransaction;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
