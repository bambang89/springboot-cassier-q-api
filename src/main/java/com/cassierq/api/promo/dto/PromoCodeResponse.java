package com.cassierq.api.promo.dto;

import com.cassierq.api.domain.entity.PromoCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(
        UUID id,
        String code,
        String name,
        String discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal minPurchaseAmount,
        Instant startsAt,
        Instant endsAt,
        Integer usageLimit,
        int usedCount,
        boolean active) {

    public static PromoCodeResponse from(PromoCode p) {
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getName(), p.getDiscountType(), p.getDiscountValue(),
                p.getMaxDiscountAmount(), p.getMinPurchaseAmount(), p.getStartsAt(), p.getEndsAt(),
                p.getUsageLimit(), p.getUsedCount(), p.isActive());
    }
}
