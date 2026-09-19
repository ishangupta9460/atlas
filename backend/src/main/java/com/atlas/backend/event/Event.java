package com.atlas.backend.event;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only Event Log shared by the Phase 0 task flow and permanent domains. */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 16)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String payload;

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
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public String getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }

    public static Event of(Long taskId, String type) {
        Event event = new Event();
        event.taskId = taskId;
        event.type = type;
        event.entityType = "task";
        event.entityId = taskId;
        event.actor = "user";
        return event;
    }

    public static Event forEntity(String entityType, Long entityId, String type, String actor) {
        return forEntity(entityType, entityId, type, actor, null);
    }

    public static Event forEntity(String entityType, Long entityId, String type, String actor, String reason) {
        return forEntity(entityType, entityId, type, actor, reason, null);
    }

    public static Event forEntity(String entityType, Long entityId, String type, String actor, String reason, String payload) {
        Event event = new Event();
        event.entityType = entityType;
        event.entityId = entityId;
        event.type = type;
        event.actor = actor;
        event.reason = reason;
        event.payload = payload;
        return event;
    }
}
