package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.ProductCategory;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String categoryCode,
        String categoryName,
        UUID parentCategoryId,
        String parentCategoryName,
        boolean active) {

    public static CategoryResponse from(ProductCategory category) {
        ProductCategory parent = category.getParentCategory();
        return new CategoryResponse(
                category.getId(),
                category.getCategoryCode(),
                category.getCategoryName(),
                parent != null ? parent.getId() : null,
                parent != null ? parent.getCategoryName() : null,
                category.isActive());
    }
}
