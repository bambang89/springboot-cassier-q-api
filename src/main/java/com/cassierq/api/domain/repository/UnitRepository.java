package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Unit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    boolean existsByUnitCodeIgnoreCase(String unitCode);
}
