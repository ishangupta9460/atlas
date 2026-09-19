package com.atlas.backend.recurringintention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRecurringIntentionTargetRequest(
        @NotNull(message = "targetCountPerWeek is required") @Min(value = 1, message = "targetCountPerWeek must be at least 1") @Max(value = 1000000, message = "targetCountPerWeek must be at most 1000000") Integer targetCountPerWeek) { }
