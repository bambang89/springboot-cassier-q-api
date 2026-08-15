package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductPrice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    // effective_until IS NULL is the DB's definition of "current price" (filtered unique index).
    Optional<ProductPrice> findByStoreIdAndProductIdAndEffectiveUntilIsNull(UUID storeId, UUID productId);
}
