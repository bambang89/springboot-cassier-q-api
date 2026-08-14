package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByStoreId(UUID storeId, Pageable pageable);

    Page<Product> findByStoreIdAndNameContainingIgnoreCase(UUID storeId, String name, Pageable pageable);

    Optional<Product> findByStoreIdAndBarcode(UUID storeId, String barcode);

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndSkuIgnoreCase(UUID storeId, String sku);
}
