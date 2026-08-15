package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findByBarcodeAndDeletedAtIsNull(String barcode);

    @Query("""
            select p from Product p
            where p.deletedAt is null
              and (:search is null
                   or lower(p.productName) like lower(concat('%', :search, '%'))
                   or lower(p.sku) like lower(concat('%', :search, '%')))
            """)
    Page<Product> search(String search, Pageable pageable);
}
