package com.atlas.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface EventJpaRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByTaskId(Long taskId);
    List<Event> findAllByEntityTypeAndEntityIdOrderByTimestamp(String entityType, Long entityId);
}
