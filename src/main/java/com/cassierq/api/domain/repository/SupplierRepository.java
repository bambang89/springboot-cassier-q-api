package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Supplier;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    boolean existsBySupplierCodeIgnoreCase(String supplierCode);
}
