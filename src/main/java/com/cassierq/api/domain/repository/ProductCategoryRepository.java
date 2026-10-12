package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findByOwnerStoreId(UUID ownerStoreId);

    Optional<ProductCategory> findByIdAndOwnerStoreId(UUID id, UUID ownerStoreId);

    boolean existsByOwnerStoreIdAndCategoryCodeIgnoreCase(UUID ownerStoreId, String categoryCode);
}
