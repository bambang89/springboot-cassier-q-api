package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.PurchaseOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

    List<PurchaseOrderItem> findByPurchaseOrderId(UUID purchaseOrderId);
}
