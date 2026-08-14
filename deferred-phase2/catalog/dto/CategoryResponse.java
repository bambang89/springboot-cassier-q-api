package com.cassierq.api.catalog.dto;

import com.cassierq.api.domain.entity.Category;
import java.util.UUID;

public record CategoryResponse(UUID id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
