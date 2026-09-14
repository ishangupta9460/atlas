package com.atlas.backend.category;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(min = 1, max = 255, message = "name must be between 1 and 255 characters") String name,
        @Pattern(regexp = "fixed|protected|flexible|optional", message = "defaultFlexibilityTier must be fixed, protected, flexible, or optional") String defaultFlexibilityTier,
        @Size(min = 1, max = 255, message = "color must be between 1 and 255 characters") String color) { }
