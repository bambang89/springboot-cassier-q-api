package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.ProductUnitConversion;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductUnitResponse(
        UUID unitId,
        String unitName,
        BigDecimal conversionToBase,
        boolean baseUnit,
        boolean purchaseUnit,
        boolean saleUnit) {

    public static ProductUnitResponse from(ProductUnitConversion conversion) {
        return new ProductUnitResponse(
                conversion.getUnit().getId(),
                conversion.getUnit().getUnitName(),
                conversion.getConversionToBase(),
                conversion.isBaseUnit(),
                conversion.isPurchaseUnit(),
                conversion.isSaleUnit());
    }
}
