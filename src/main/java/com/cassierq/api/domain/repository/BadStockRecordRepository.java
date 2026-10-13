package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.BadStockRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BadStockRecordRepository extends JpaRepository<BadStockRecord, UUID> {

    Optional<BadStockRecord> findByIdAndStoreId(UUID id, UUID storeId);

    @Query("""
            select b from BadStockRecord b join fetch b.product join fetch b.unit join fetch b.reportedBy r join fetch r.employee
            where b.store.id = :storeId and (:status is null or b.status = :status)
            order by b.createdAt desc
            """)
    List<BadStockRecord> list(@Param("storeId") UUID storeId, @Param("status") String status);

    /** Records whose stock was taken out, in a period — for the loss report. */
    @Query("""
            select b from BadStockRecord b join fetch b.product
            where b.store.id = :storeId and b.stockDeducted = true
              and b.createdAt >= :from and b.createdAt < :to
            """)
    List<BadStockRecord> deductedBetween(@Param("storeId") UUID storeId, @Param("from") Instant from, @Param("to") Instant to);
}
