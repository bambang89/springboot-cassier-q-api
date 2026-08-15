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
 * Maps to the pre-existing `inventories` table — current stock of a product
 * at a store, one row per (store, product) (DB-enforced unique index).
 *
 * <p>The table also has a SQL Server {@code rowversion} column
 * ({@code row_version}) for optimistic concurrency, which we deliberately
 * don't map — stock updates instead rely on a pessimistic row lock within a
 * single transaction (see {@code InventoryRepository.findForUpdate}). Simpler
 * and enough for this phase; revisit if concurrent-sale contention shows up.
 */
@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_base_unit", nullable = false)
    private BigDecimal quantityBaseUnit;

    @Column(name = "minimum_stock", nullable = false)
    @Builder.Default
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "maximum_stock")
    private BigDecimal maximumStock;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
