package com.atlas.backend.recurringintention;

import java.time.Instant;

public record RecurringIntentionResponse(Long id, Long goalId, String title, int targetCountPerWeek,
                                         int currentWeekRemainingCount, Long categoryId, String flexibilityTier,
                                         Instant createdAt) {
    static RecurringIntentionResponse from(RecurringIntention intention) {
        return new RecurringIntentionResponse(intention.getId(), intention.getGoalId(), intention.getTitle(),
                intention.getTargetCountPerWeek(), intention.getCurrentWeekRemainingCount(), intention.getCategoryId(),
                intention.getFlexibilityTier(), intention.getCreatedAt());
    }
}
