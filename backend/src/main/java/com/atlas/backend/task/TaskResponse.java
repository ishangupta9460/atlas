package com.atlas.backend.task;

import java.time.Instant;

/** Public representation of a minimal walking-skeleton task. */
public record TaskResponse(
        Long id,
        String title,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(), task.getTitle(), task.getStatus(), task.getCreatedAt(),
                task.getStartedAt(), task.getFinishedAt());
    }
}
