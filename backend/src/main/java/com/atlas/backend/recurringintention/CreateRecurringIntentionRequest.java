package com.atlas.backend.recurringintention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRecurringIntentionRequest(
        Long goalId,
        @NotBlank(message = "title must not be blank") @Size(max = 255, message = "title must be at most 255 characters") String title,
        @NotNull(message = "targetCountPerWeek is required") @Min(value = 1, message = "targetCountPerWeek must be at least 1") @Max(value = 1000000, message = "targetCountPerWeek must be at most 1000000") Integer targetCountPerWeek,
        Long categoryId,
        @NotBlank(message = "flexibilityTier is required")
        @Pattern(regexp = "fixed|protected|flexible|optional", message = "flexibilityTier must be fixed, protected, flexible, or optional") String flexibilityTier) { }
