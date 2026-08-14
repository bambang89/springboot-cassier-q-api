package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String barcode,
        String name,
        BigDecimal price,
        Integer stock,
        UUID categoryId,
        String categoryName,
        String imageUrl) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getImageUrl());
    }
}
