package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Order;
import com.cassierq.api.domain.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByStoreIdOrderByCreatedAtDesc(UUID storeId, Pageable pageable);

    Optional<Order> findByIdAndStoreId(UUID id, UUID storeId);

    @Query("""
        select coalesce(sum(o.total), 0) from Order o
        where o.store.id = :storeId and o.status = :status
        and o.createdAt >= :from and o.createdAt < :to
        """)
    BigDecimal sumTotalByStoreAndStatusAndCreatedAtBetween(
            @Param("storeId") UUID storeId,
            @Param("status") OrderStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        select count(o) from Order o
        where o.store.id = :storeId and o.status = :status
        and o.createdAt >= :from and o.createdAt < :to
        """)
    long countByStoreAndStatusAndCreatedAtBetween(
            @Param("storeId") UUID storeId,
            @Param("status") OrderStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
