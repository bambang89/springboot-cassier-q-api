package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductCategory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    boolean existsByCategoryCodeIgnoreCase(String categoryCode);
}
