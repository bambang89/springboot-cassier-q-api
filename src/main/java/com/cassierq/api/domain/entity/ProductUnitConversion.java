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
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to the pre-existing `product_unit_conversions` table: how many base units one of `unit` is worth. */
@Entity
@Table(name = "product_unit_conversions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUnitConversion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @Column(name = "conversion_to_base", nullable = false)
    private BigDecimal conversionToBase;

    @Column(name = "is_base_unit", nullable = false)
    @Builder.Default
    private boolean baseUnit = false;

    @Column(name = "is_purchase_unit", nullable = false)
    @Builder.Default
    private boolean purchaseUnit = false;

    @Column(name = "is_sale_unit", nullable = false)
    @Builder.Default
    private boolean saleUnit = true;
}
