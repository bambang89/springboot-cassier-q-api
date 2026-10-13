package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Unit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    List<Unit> findByOwnerStoreId(UUID ownerStoreId);

    Optional<Unit> findByIdAndOwnerStoreId(UUID id, UUID ownerStoreId);

    boolean existsByOwnerStoreIdAndUnitCodeIgnoreCase(UUID ownerStoreId, String unitCode);

    Optional<Unit> findFirstByOwnerStoreIdAndUnitCodeIgnoreCase(UUID ownerStoreId, String unitCode);
}
