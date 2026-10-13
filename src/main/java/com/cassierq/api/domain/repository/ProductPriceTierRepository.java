package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductPriceTier;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPriceTierRepository extends JpaRepository<ProductPriceTier, UUID> {

    List<ProductPriceTier> findByStoreIdAndProductIdOrderByMinQuantityAsc(UUID storeId, UUID productId);

    void deleteByStoreIdAndProductId(UUID storeId, UUID productId);
}
