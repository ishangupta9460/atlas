package com.atlas.backend.goal;

import java.time.Instant;
import java.time.LocalDate;

public record GoalResponse(
        Long id,
        String title,
        String description,
        LocalDate targetDeadline,
        String lifecycleState,
        String planningState,
        Instant createdAt) {
    static GoalResponse from(Goal goal) {
        return new GoalResponse(goal.getId(), goal.getTitle(), goal.getDescription(),
                goal.getTargetDeadline(), goal.getLifecycleState(), goal.getPlanningState(), goal.getCreatedAt());
    }
}
