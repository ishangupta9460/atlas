package com.atlas.backend.roadmap;

import jakarta.persistence.*;
import java.time.Instant;

/** Structural plan for one Goal; Roadmaps are never directly schedulable. */
@Entity
@Table(name = "roadmaps")
public class Roadmap {
    public static final String USER_INTERVIEW = "user_interview";
    public static final String IMPORTED = "imported";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false, unique = true)
    private Long goalId;

    @Column(nullable = false, length = 32, updatable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getGoalId() { return goalId; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }

    static Roadmap create(Long goalId, String source) {
        Roadmap roadmap = new Roadmap();
        roadmap.goalId = goalId;
        roadmap.source = source == null ? USER_INTERVIEW : source;
        return roadmap;
    }
}
