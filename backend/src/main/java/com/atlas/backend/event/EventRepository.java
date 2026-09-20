package com.atlas.backend.event;

import java.util.List;
import java.util.Optional;

/** Application-facing append-only Event Log access. */
public interface EventRepository {
    Event append(Event event);
    Event appendAndFlush(Event event);
    long count();
    List<Event> findAll();
    Optional<Event> findById(Long id);
    List<Event> findAllByTaskId(Long taskId);
    List<Event> findAllByEntityTypeAndEntityIdOrderByTimestamp(String entityType, Long entityId);
    void deleteAllForTest();
}
