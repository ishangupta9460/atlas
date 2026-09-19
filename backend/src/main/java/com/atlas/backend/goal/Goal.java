package com.atlas.backend.goal;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Permanent Goal entity with independent lifecycle and planning-state axes. */
@Entity
@Table(name = "goals")
public class Goal {

    public static final String ACTIVE = "active";
    public static final String COMPLETED = "completed";
    public static final String ABANDONED = "abandoned";
    public static final String DEFERRED = "deferred";
    public static final String PAUSED = "paused";
    public static final String AT_RISK = "at_risk";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_deadline")
    private LocalDate targetDeadline;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState;

    @Column(name = "planning_state", nullable = false, length = 32)
    private String planningState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getTargetDeadline() { return targetDeadline; }
    public String getLifecycleState() { return lifecycleState; }
    public String getPlanningState() { return planningState; }
    public Instant getCreatedAt() { return createdAt; }

    void update(String title, LocalDate targetDeadline, boolean deadlineProvided) {
        if (title != null) {
            this.title = title;
        }
        if (deadlineProvided) {
            this.targetDeadline = targetDeadline;
        }
    }

    void complete() { lifecycleState = COMPLETED; }
    void abandon() { lifecycleState = ABANDONED; }
    void defer() { planningState = DEFERRED; }
    void reactivate() { planningState = ACTIVE; }
    void markAtRisk() { planningState = AT_RISK; }
    void resolveRisk() { planningState = ACTIVE; }
    void pause() { planningState = PAUSED; }
    void resume() { planningState = ACTIVE; }

    static Goal create(Long userId, String title, String description, LocalDate targetDeadline) {
        Goal goal = new Goal();
        goal.userId = userId;
        goal.title = title;
        goal.description = description;
        goal.targetDeadline = targetDeadline;
        goal.lifecycleState = ACTIVE;
        goal.planningState = ACTIVE;
        return goal;
    }
}
