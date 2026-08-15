package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.SalesTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, UUID> {

    Optional<SalesTransaction> findByIdAndStoreId(UUID id, UUID storeId);

    Page<SalesTransaction> findByStoreIdOrderByTransactionDateDesc(UUID storeId, Pageable pageable);

    @Query("""
            select count(t) from SalesTransaction t
            where t.store.id = :storeId and t.transactionStatus = 'PAID'
              and t.transactionDate between :from and :to
            """)
    long countPaid(UUID storeId, Instant from, Instant to);

    @Query("""
            select coalesce(sum(t.grandTotal), 0) from SalesTransaction t
            where t.store.id = :storeId and t.transactionStatus = 'PAID'
              and t.transactionDate between :from and :to
            """)
    BigDecimal sumGrandTotalPaid(UUID storeId, Instant from, Instant to);
}
