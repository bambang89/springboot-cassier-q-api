package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.CashierSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashierSessionRepository extends JpaRepository<CashierSession, UUID> {

    Optional<CashierSession> findByCashierIdAndStatus(UUID cashierId, String status);
}
