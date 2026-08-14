package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByStoreIdOrderByNameAsc(UUID storeId);
}
