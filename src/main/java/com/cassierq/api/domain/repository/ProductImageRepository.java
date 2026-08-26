package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAscCreatedAtAsc(UUID productId);

    long countByProductId(UUID productId);
}
