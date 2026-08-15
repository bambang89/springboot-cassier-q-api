package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
}
