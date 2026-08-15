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
 * Maps to the pre-existing `cashier_sessions` table — a cash-drawer session a
 * cashier opens before ringing up sales and closes at end of shift. DB
 * enforces at most one OPEN session per cashier (filtered unique index).
 */
@Entity
@Table(name = "cashier_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashierSession extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_cash", nullable = false)
    private BigDecimal openingCash;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash;

    @Column(name = "actual_cash")
    private BigDecimal actualCash;

    @Column(name = "cash_difference")
    private BigDecimal cashDifference;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String status = "OPEN";

    @Column(length = 1000)
    private String notes;
}
