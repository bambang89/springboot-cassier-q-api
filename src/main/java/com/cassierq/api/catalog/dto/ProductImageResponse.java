package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.ProductImage;
import java.util.UUID;

public record ProductImageResponse(UUID id, String imageUrl, Integer sortOrder) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl(), image.getSortOrder());
    }
}
