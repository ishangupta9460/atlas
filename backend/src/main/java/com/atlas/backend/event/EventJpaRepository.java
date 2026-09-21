package com.atlas.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface EventJpaRepository extends JpaRepository<Event, Long> {
    @org.springframework.data.jpa.repository.Query("select e from Event e where e.entityType = :entityType and e.entityId = :entityId and (:actor is null or e.actor = :actor) and e.id < :cursor order by e.id desc")
    List<Event> recent(String entityType, Long entityId, String actor, Long cursor,
            org.springframework.data.domain.Pageable page);
    List<Event> findAllByTaskId(Long taskId);
    List<Event> findAllByEntityTypeAndEntityIdOrderByTimestamp(String entityType, Long entityId);
}
