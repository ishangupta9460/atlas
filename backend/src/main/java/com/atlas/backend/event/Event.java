package com.atlas.backend.event;

import jakarta.persistence.*;
import java.time.Instant;

/** Minimal event entity for the FOUND-003 walking skeleton. */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    private void prePersist() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getType() { return type; }
    public Instant getTimestamp() { return timestamp; }

    public static Event of(Long taskId, String type) {
        Event event = new Event();
        event.taskId = taskId;
        event.type = type;
        return event;
    }
}
