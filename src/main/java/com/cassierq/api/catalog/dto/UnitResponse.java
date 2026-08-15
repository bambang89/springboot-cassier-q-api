package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.Unit;
import java.util.UUID;

public record UnitResponse(UUID id, String unitCode, String unitName) {

    public static UnitResponse from(Unit unit) {
        return new UnitResponse(unit.getId(), unit.getUnitCode(), unit.getUnitName());
    }
}
