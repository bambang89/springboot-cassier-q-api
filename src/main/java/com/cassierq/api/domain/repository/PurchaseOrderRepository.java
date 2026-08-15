package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndStoreId(UUID id, UUID storeId);

    Page<PurchaseOrder> findByStoreIdOrderByOrderDateDesc(UUID storeId, Pageable pageable);
}
