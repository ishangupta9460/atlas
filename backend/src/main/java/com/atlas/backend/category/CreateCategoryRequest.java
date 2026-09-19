package com.atlas.backend.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank(message = "name must not be blank") @Size(max = 255, message = "name must be at most 255 characters") String name,
        @NotBlank(message = "defaultFlexibilityTier is required")
        @Pattern(regexp = "fixed|protected|flexible|optional", message = "defaultFlexibilityTier must be fixed, protected, flexible, or optional") String defaultFlexibilityTier,
        @NotBlank(message = "color must not be blank") @Size(max = 255, message = "color must be at most 255 characters") String color, tools.jackson.databind.JsonNode defaultImportance) {
    public CreateCategoryRequest(String name, String defaultFlexibilityTier, String color) { this(name, defaultFlexibilityTier, color, null); }
}
