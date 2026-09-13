package com.atlas.backend.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the minimal FOUND-003 event table. */
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByTaskId(Long taskId);

    List<Event> findAllByEntityTypeAndEntityIdOrderByTimestamp(String entityType, Long entityId);
}
