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

    // Matches name, SKU, barcode, or brand — whichever the client typed into
    // one search box (a cashier might scan-paste a barcode here just as
    // often as type a product name).
    @Query("""
            select p from Product p
            where p.deletedAt is null
              and (:search is null
                   or lower(p.productName) like lower(concat('%', :search, '%'))
                   or lower(p.sku) like lower(concat('%', :search, '%'))
                   or lower(p.barcode) like lower(concat('%', :search, '%'))
                   or lower(p.brand) like lower(concat('%', :search, '%')))
            """)
    Page<Product> search(String search, Pageable pageable);
}
