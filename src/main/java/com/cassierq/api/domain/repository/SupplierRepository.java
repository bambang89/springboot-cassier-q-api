package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByOwnerStoreId(UUID ownerStoreId);

    Optional<Supplier> findByIdAndOwnerStoreId(UUID id, UUID ownerStoreId);

    boolean existsByOwnerStoreIdAndSupplierCodeIgnoreCase(UUID ownerStoreId, String supplierCode);
}
