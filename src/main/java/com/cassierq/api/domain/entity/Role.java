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

/**
 * Maps to the pre-existing `roles` table — the fixed set of role codes
 * (SUPERADMIN, KEPALA_TOKO, PRODUCT, GUDANG, KASIR, ...) a {@link User} can
 * be granted via {@link UserRole}.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_code", nullable = false, unique = true, length = 60)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 200)
    private String roleName;

    @Column(length = 510)
    private String description;

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private boolean systemRole = false;
}
