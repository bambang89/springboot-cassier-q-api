package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String barcode,
        String productName,
        UUID categoryId,
        String categoryName,
        String brand,
        String description,
        UUID baseUnitId,
        String baseUnitName,
        String status,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        BigDecimal stockQuantity) {

    public static ProductResponse from(Product product, BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal stockQuantity) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getProductName(),
                product.getCategory().getId(),
                product.getCategory().getCategoryName(),
                product.getBrand(),
                product.getDescription(),
                product.getBaseUnit().getId(),
                product.getBaseUnit().getUnitName(),
                product.getStatus(),
                sellingPrice,
                costPrice,
                stockQuantity);
    }
}
