package com.atlas.backend.recurringintention;

import jakarta.persistence.*;
import java.time.Instant;

/** A repeated weekly target; the remaining count is intentionally only current-week state. */
@Entity
@Table(name = "recurring_intentions")
public class RecurringIntention {
    public static final String FIXED = "fixed";
    public static final String PROTECTED = "protected";
    public static final String FLEXIBLE = "flexible";
    public static final String OPTIONAL = "optional";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "goal_id")
    private Long goalId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(name = "target_count_per_week", nullable = false)
    private int targetCountPerWeek;
    @Column(name = "current_week_remaining_count", nullable = false)
    private int currentWeekRemainingCount;
    @Column(name = "category_id")
    private Long categoryId;
    @Column(name = "flexibility_tier", nullable = false, length = 16)
    private String flexibilityTier;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist private void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getGoalId() { return goalId; }
    public String getTitle() { return title; }
    public int getTargetCountPerWeek() { return targetCountPerWeek; }
    public int getCurrentWeekRemainingCount() { return currentWeekRemainingCount; }
    public Long getCategoryId() { return categoryId; }
    public String getFlexibilityTier() { return flexibilityTier; }
    public Instant getCreatedAt() { return createdAt; }

    void updateTarget(int targetCountPerWeek) { this.targetCountPerWeek = targetCountPerWeek; }
    void resetForNewWeek() { currentWeekRemainingCount = targetCountPerWeek; }
    void completeInstance() { currentWeekRemainingCount = Math.max(0, currentWeekRemainingCount - 1); }

    static RecurringIntention create(Long userId, Long goalId, String title, int targetCountPerWeek,
                                     Long categoryId, String flexibilityTier) {
        RecurringIntention intention = new RecurringIntention();
        intention.userId = userId;
        intention.goalId = goalId;
        intention.title = title;
        intention.targetCountPerWeek = targetCountPerWeek;
        intention.currentWeekRemainingCount = targetCountPerWeek;
        intention.categoryId = categoryId;
        intention.flexibilityTier = flexibilityTier;
        return intention;
    }
}
