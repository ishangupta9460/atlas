package com.atlas.backend.category;

import java.time.Instant;

public record CategoryResponse(Long id, String name, String defaultFlexibilityTier, String color, Instant createdAt) {
    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDefaultFlexibilityTier(),
                category.getColor(), category.getCreatedAt());
    }
}
