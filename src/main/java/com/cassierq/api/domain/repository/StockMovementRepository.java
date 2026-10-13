package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** Store movements, newest first, optionally filtered by product, type and period. */
    @org.springframework.data.jpa.repository.Query(
            value = """
                    select m from StockMovement m join fetch m.product join fetch m.unit
                    left join fetch m.createdBy u left join fetch u.employee
                    where m.store.id = :storeId
                      and (:productId is null or m.product.id = :productId)
                      and (:type is null or m.movementType = :type)
                      and (:from is null or m.createdAt >= :from)
                      and (:to is null or m.createdAt < :to)
                    order by m.createdAt desc
                    """,
            countQuery = """
                    select count(m) from StockMovement m
                    where m.store.id = :storeId
                      and (:productId is null or m.product.id = :productId)
                      and (:type is null or m.movementType = :type)
                      and (:from is null or m.createdAt >= :from)
                      and (:to is null or m.createdAt < :to)
                    """)
    org.springframework.data.domain.Page<StockMovement> search(
            @org.springframework.data.repository.query.Param("storeId") UUID storeId,
            @org.springframework.data.repository.query.Param("productId") UUID productId,
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to,
            org.springframework.data.domain.Pageable pageable);

    java.util.List<StockMovement> findByReferenceTypeAndReferenceIdAndMovementType(
            String referenceType, UUID referenceId, String movementType);
}
