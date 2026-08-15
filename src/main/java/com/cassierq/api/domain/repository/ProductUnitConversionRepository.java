package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.ProductUnitConversion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductUnitConversionRepository extends JpaRepository<ProductUnitConversion, UUID> {

    Optional<ProductUnitConversion> findByProductIdAndUnitId(UUID productId, UUID unitId);

    Optional<ProductUnitConversion> findByProductIdAndBaseUnitTrue(UUID productId);
}
