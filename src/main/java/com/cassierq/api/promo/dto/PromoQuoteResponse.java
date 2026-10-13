package com.cassierq.api.promo.dto;

import java.math.BigDecimal;

/** Result of checking a promo code against a cart subtotal at the POS. */
public record PromoQuoteResponse(String code, String name, String description, BigDecimal discountAmount) {
}
