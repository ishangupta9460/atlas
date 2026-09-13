package com.atlas.backend.task;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Minimal task entity for the FOUND-003 walking skeleton.
 *
 * <p>This is intentionally not the full Commitment model from DOM-003.
 */
@Entity
@Table(name = "tasks")
public class Task {

    public static final String READY = "ready";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    void start(Instant startedAt) {
        status = IN_PROGRESS;
        this.startedAt = startedAt;
    }

    void finish(Instant finishedAt) {
        status = COMPLETED;
        this.finishedAt = finishedAt;
    }

    public static Task ready(Long userId, String title) {
        Task task = new Task();
        task.userId = userId;
        task.title = title;
        task.status = READY;
        return task;
    }
}
