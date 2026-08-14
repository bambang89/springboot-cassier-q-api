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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to the pre-existing `employees` table. Every login {@link User} has
 * exactly one employee record (the person behind the account) — see the
 * unique `users.employee_id` FK.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_code", nullable = false, length = 60)
    private String employeeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(length = 60)
    private String phone;

    @Column(length = 300)
    private String email;

    @Column(length = 510)
    private String address;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
