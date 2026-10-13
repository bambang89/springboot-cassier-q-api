package com.cassierq.api.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item inside a bundle.
 *
 * @param quantity       amount of {@code unitName} per bundle
 * @param quantityBase   same amount in the product's base unit
 * @param stockBase      current stock of the item in this store (base unit)
 */
public record BundleItemResponse(
        UUID productId,
        String productName,
        UUID unitId,
        String unitName,
        BigDecimal quantity,
        BigDecimal quantityBase,
        BigDecimal stockBase) {
}
