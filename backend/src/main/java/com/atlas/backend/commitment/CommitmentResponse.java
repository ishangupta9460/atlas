package com.atlas.backend.commitment;

import java.math.BigDecimal;
import java.time.Instant;

public record CommitmentResponse(Long id, Long milestoneId, Long goalId, Long categoryId, String title,
        String description, String completionCriterion, Instant ownDeadline, boolean isHardConsequence,
        String importance, String flexibilityTier, String workState, boolean userMovedFlag,
        BigDecimal currentCompletionPct, Instant createdAt) {
    static CommitmentResponse from(Commitment c) {
        return new CommitmentResponse(c.getId(),c.getMilestoneId(),c.getGoalId(),c.getCategoryId(),c.getTitle(),c.getDescription(),
            c.getCompletionCriterion(),c.getOwnDeadline(),c.isHardConsequence(),c.getImportance(),c.getFlexibilityTier(),c.getWorkState(),
            c.isUserMovedFlag(),c.getCurrentCompletionPct(),c.getCreatedAt());
    }
}
