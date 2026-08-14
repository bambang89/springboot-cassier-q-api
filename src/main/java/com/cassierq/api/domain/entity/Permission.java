package com.cassierq.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the pre-existing `permissions` table. Note: unlike most tables
 * here it only has {@code created_at} (no {@code updated_at}), so this does
 * NOT extend {@link Auditable}.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "permission_code", nullable = false, unique = true, length = 120)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 300)
    private String permissionName;

    @Column(nullable = false, length = 100)
    private String module;

    @Column(length = 510)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
