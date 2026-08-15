package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByStoreIdAndProductId(UUID storeId, UUID productId);

    // Pessimistic row lock so two concurrent sales of the same product can't
    // both read the same starting quantity and oversell stock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.store.id = :storeId and i.product.id = :productId")
    Optional<Inventory> findForUpdate(UUID storeId, UUID productId);
}
