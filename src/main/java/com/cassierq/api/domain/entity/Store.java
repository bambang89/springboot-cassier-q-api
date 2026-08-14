package com.cassierq.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to the pre-existing `stores` table (owned by the sibling Go backend's schema — not created by our Flyway). */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "store_code", nullable = false, length = 40)
    private String storeCode;

    @Column(name = "store_name", nullable = false, length = 300)
    private String storeName;

    @Column(length = 510)
    private String address;

    @Column(length = 200)
    private String province;

    @Column(length = 200)
    private String city;

    @Column(length = 60)
    private String phone;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "is_head_office", nullable = false)
    @Builder.Default
    private boolean headOffice = false;
}
