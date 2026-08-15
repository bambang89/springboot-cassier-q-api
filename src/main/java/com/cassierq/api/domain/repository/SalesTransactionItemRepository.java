package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.SalesTransactionItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesTransactionItemRepository extends JpaRepository<SalesTransactionItem, UUID> {

    List<SalesTransactionItem> findByTransactionId(UUID transactionId);

    @Query("""
            select i.product.id as productId, i.product.productName as productName,
                   sum(i.quantityBaseUnit) as totalQuantity, sum(i.subtotal) as totalRevenue
            from SalesTransactionItem i
            where i.transaction.store.id = :storeId
              and i.transaction.transactionStatus = 'PAID'
              and i.transaction.transactionDate between :from and :to
            group by i.product.id, i.product.productName
            order by sum(i.quantityBaseUnit) desc
            """)
    List<BestSellerRow> findBestSellers(UUID storeId, java.time.Instant from, java.time.Instant to,
            org.springframework.data.domain.Pageable pageable);

    interface BestSellerRow {
        UUID getProductId();
        String getProductName();
        java.math.BigDecimal getTotalQuantity();
        java.math.BigDecimal getTotalRevenue();
    }
}
