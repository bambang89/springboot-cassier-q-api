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
 * A bad-stock report (V16): damaged, expired, lost, defective or customer-returned goods.
 * Status flow: PENDING → QUARANTINED → RETURNED / DISPOSED, or → CANCELLED.
 * {@code stockDeducted} tells whether sellable stock was already reduced.
 */
@Entity
@Table(name = "bad_stock_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadStockRecord extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "quantity_base", nullable = false)
    private BigDecimal quantityBase;

    /** DAMAGED, EXPIRED, LOST, DEFECT, CUSTOMER_RETURN. */
    @Column(nullable = false, length = 20)
    private String reason;

    @Column(length = 500)
    private String notes;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** PENDING, QUARANTINED, RETURNED, DISPOSED, CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Loss value at cost price when reported (null when the product has no cost price). */
    @Column(name = "cost_value")
    private BigDecimal costValue;

    @Column(name = "stock_deducted", nullable = false)
    @Builder.Default
    private boolean stockDeducted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;
}
