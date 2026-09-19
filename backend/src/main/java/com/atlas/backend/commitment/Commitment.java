package com.atlas.backend.commitment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Entity
@Table(name = "commitments")
public class Commitment {
    public static final Set<String> STATES = Set.of("draft", "ready", "deferred", "in_progress", "completed", "cancelled");
    public static final Set<String> IMPORTANCE = Set.of("low", "medium", "high", "critical");
    public static final Set<String> FLEXIBILITY = Set.of("fixed", "protected", "flexible", "optional");
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false, updatable=false) private Long userId;
    @Column(name="milestone_id") private Long milestoneId;
    @Column(name="goal_id") private Long goalId;
    @Column(name="category_id") private Long categoryId;
    @Column(length=255) private String title;
    @Column(columnDefinition="TEXT") private String description;
    @Column(name="completion_criterion", columnDefinition="TEXT") private String completionCriterion;
    @Convert(converter=UtcInstantConverter.class) @Column(name="own_deadline") private Instant ownDeadline;
    @Column(name="is_hard_consequence", nullable=false) private boolean hardConsequence;
    @Column(nullable=false, length=16) private String importance;
    @Column(name="flexibility_tier", nullable=false, length=16) private String flexibilityTier;
    @Column(name="work_state", nullable=false, length=32) private String workState;
    @Column(name="user_moved_flag", nullable=false) private boolean userMovedFlag;
    @Column(name="current_completion_pct", nullable=false, precision=5, scale=2) private BigDecimal currentCompletionPct = new BigDecimal("0.00");
    @Convert(converter=UtcInstantConverter.class) @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    protected Commitment() { }
    static Commitment create(Long owner, Fields fields) {
        if (owner == null) throw CommitmentException.invalid("Owner required");
        Commitment value = new Commitment();
        value.userId = owner;
        value.workState = "draft";
        value.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        value.update(fields);
        return value;
    }
    record Fields(String title, String description, String criterion, Instant deadline, Long milestone, Long goal, Long category, String importance, String flexibility) { }
    void update(Fields f) {
        if (f.title() != null && f.title().length() > 255) throw CommitmentException.invalid("title must be at most 255 characters");
        if (f.importance() == null || !IMPORTANCE.contains(f.importance())) throw CommitmentException.invalid("A valid importance or category default is required");
        if (f.flexibility() == null || !FLEXIBILITY.contains(f.flexibility())) throw CommitmentException.invalid("A valid flexibilityTier or category default is required");
        if (!"draft".equals(workState) && (!text(f.title()) || !text(f.criterion()))) throw CommitmentException.state();
        if (f.milestone() != null && f.goal() == null) throw CommitmentException.invalid("Milestone requires its Goal");
        Instant deadline = normalize(f.deadline());
        title=f.title(); description=f.description(); completionCriterion=f.criterion(); ownDeadline=deadline;
        milestoneId=f.milestone(); goalId=f.goal(); categoryId=f.category(); importance=f.importance(); flexibilityTier=f.flexibility();
    }
    boolean establishReadiness() {
        if ("draft".equals(workState) && text(title) && text(completionCriterion)) { transition("ready", false, false); return true; }
        return false;
    }
    /** Domain-only guard. No public route can supply state or execution attestations. */
    void transition(String target, boolean scheduledBlockPlaced, boolean userTrigger) {
        boolean allowed = switch (workState) {
            case "draft" -> "ready".equals(target) && text(title) && text(completionCriterion);
            case "ready" -> "in_progress".equals(target) && scheduledBlockPlaced && userTrigger;
            case "in_progress" -> userTrigger && ("completed".equals(target) || "ready".equals(target));
            default -> false;
        };
        if (!allowed) throw CommitmentException.state();
        workState=target;
    }
    static boolean text(String value) { return value != null && !value.isBlank(); }
    static Instant normalize(Instant value) {
        if (value == null) return null;
        if (value.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || !value.isBefore(Instant.parse("+10000-01-01T00:00:00Z")))
            throw CommitmentException.invalid("ownDeadline must be within UTC years 1000 through 9999");
        return value.truncatedTo(ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getMilestoneId() { return milestoneId; }
    public Long getGoalId() { return goalId; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCompletionCriterion() { return completionCriterion; }
    public Instant getOwnDeadline() { return ownDeadline; }
    public boolean isHardConsequence() { return hardConsequence; }
    public String getImportance() { return importance; }
    public String getFlexibilityTier() { return flexibilityTier; }
    public String getWorkState() { return workState; }
    public boolean isUserMovedFlag() { return userMovedFlag; }
    public BigDecimal getCurrentCompletionPct() { return currentCompletionPct; }
    public Instant getCreatedAt() { return createdAt; }
}
