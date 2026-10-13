package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.CashierSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashierSessionRepository extends JpaRepository<CashierSession, UUID> {

    Optional<CashierSession> findByCashierIdAndStatus(UUID cashierId, String status);

    List<CashierSession> findByStatusAndOpenedAtBefore(String status, Instant openedBefore);

    /**
     * Closes a session only if it is still OPEN, so two app instances (blue-green
     * overlap) or a manual close racing the scheduler cannot close it twice.
     * Returns the number of rows changed (0 = already closed).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CashierSession s
               set s.status = 'CLOSED',
                   s.closedAt = :closedAt,
                   s.expectedCash = :expectedCash,
                   s.notes = :notes,
                   s.updatedAt = :closedAt
             where s.id = :id and s.status = 'OPEN'
            """)
    int autoCloseIfOpen(
            @Param("id") UUID id,
            @Param("closedAt") Instant closedAt,
            @Param("expectedCash") BigDecimal expectedCash,
            @Param("notes") String notes);
}
