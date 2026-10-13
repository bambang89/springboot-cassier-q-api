package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductBundleItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBundleItemRepository extends JpaRepository<ProductBundleItem, UUID> {

    List<ProductBundleItem> findByBundleIdOrderByCreatedAtAsc(UUID bundleProductId);

    void deleteByBundleId(UUID bundleProductId);

    boolean existsByComponentId(UUID componentProductId);
}
