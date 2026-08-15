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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the pre-existing `number_sequences` table — one counter per
 * (store, sequence_type), pre-seeded (e.g. SALES_TRANSACTION/TRX,
 * STOCK_OPNAME/SO, ...). We only ever read + increment {@code current_value},
 * never insert new rows. Only has {@code updated_at}, no {@code created_at}.
 */
@Entity
@Table(name = "number_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "sequence_type", nullable = false, length = 60)
    private String sequenceType;

    @Column(nullable = false, length = 40)
    private String prefix;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
