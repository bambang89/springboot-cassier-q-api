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

/** Maps to the pre-existing `sales_transactions` table — a POS sale (what the old app called an "order"). */
@Entity
@Table(name = "sales_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesTransaction extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_number", nullable = false, unique = true, length = 60)
    private String transactionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_session_id", nullable = false)
    private CashierSession cashierSession;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "payment_amount", nullable = false)
    private BigDecimal paymentAmount;

    @Column(name = "change_amount", nullable = false)
    private BigDecimal changeAmount;

    @Column(name = "transaction_status", nullable = false, length = 40)
    @Builder.Default
    private String transactionStatus = "DRAFT";

    @Column(name = "void_reason", length = 510)
    private String voidReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;
}
