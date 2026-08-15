package com.cassierq.api.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitConversionResponse(
        UUID productId,
        UUID fromUnitId,
        String fromUnitName,
        BigDecimal quantity,
        UUID baseUnitId,
        String baseUnitName,
        BigDecimal conversionToBase,
        BigDecimal quantityBaseUnit) {
}
