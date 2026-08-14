package com.cassierq.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the pre-existing `users` table — a login account. Every user must
 * point at exactly one {@link Employee} (the person); which store(s) and
 * role(s) they act as come from {@link UserRole} rows, not a column here.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(length = 300)
    private String email;

    @Column(length = 60)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 510)
    private String passwordHash;

    @Column(name = "is_superadmin", nullable = false)
    @Builder.Default
    private boolean superadmin = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
