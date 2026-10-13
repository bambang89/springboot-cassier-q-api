package com.cassierq.api.catalog.dto;

import com.cassierq.api.common.storage.PublicUrls;
import com.cassierq.api.domain.entity.Product;
import java.math.BigDecimal;
import java.util.List;
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
        String imageUrl,
        UUID baseUnitId,
        String baseUnitName,
        String status,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        BigDecimal stockQuantity,
        // SINGLE or BUNDLE; for a bundle stockQuantity = whole bundles the items' stock allows.
        String productType,
        List<BundleItemResponse> bundleItems,
        // Wholesale price steps (per base unit) in this store; empty when none.
        List<PriceTierDto> priceTiers) {

    public static ProductResponse from(Product product, BigDecimal sellingPrice, BigDecimal costPrice, BigDecimal stockQuantity) {
        return from(product, sellingPrice, costPrice, stockQuantity, null);
    }

    public static ProductResponse from(
            Product product,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            BigDecimal stockQuantity,
            List<BundleItemResponse> bundleItems) {
        return from(product, sellingPrice, costPrice, stockQuantity, bundleItems, List.of());
    }

    public static ProductResponse from(
            Product product,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            BigDecimal stockQuantity,
            List<BundleItemResponse> bundleItems,
            List<PriceTierDto> priceTiers) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getProductName(),
                product.getCategory().getId(),
                product.getCategory().getCategoryName(),
                product.getBrand(),
                product.getDescription(),
                PublicUrls.resolve(product.getImageUrl()),
                product.getBaseUnit().getId(),
                product.getBaseUnit().getUnitName(),
                product.getStatus(),
                sellingPrice,
                costPrice,
                stockQuantity,
                product.getProductType(),
                bundleItems,
                priceTiers);
    }
}
