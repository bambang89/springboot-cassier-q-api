package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.OrderItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    interface BestSellerRow {
        String getProductName();

        Long getTotalQuantity();

        BigDecimal getTotalRevenue();
    }

    @Query("""
        select oi.productName as productName,
               sum(oi.quantity) as totalQuantity,
               sum(oi.lineTotal) as totalRevenue
        from OrderItem oi
        where oi.order.store.id = :storeId
        and oi.order.status = com.cassierq.api.domain.entity.OrderStatus.PAID
        and oi.order.createdAt >= :from and oi.order.createdAt < :to
        group by oi.productName
        order by sum(oi.quantity) desc
        """)
    List<BestSellerRow> findBestSellers(
            @Param("storeId") UUID storeId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
