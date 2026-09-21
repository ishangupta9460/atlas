package com.atlas.backend.event;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
class JpaEventRepository implements EventRepository {
    private final EventJpaRepository events;
    private final Environment environment;
    JpaEventRepository(EventJpaRepository events, Environment environment) { this.events = events; this.environment = environment; }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Event append(Event event) { return events.save(newEvent(event)); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Event appendAndFlush(Event event) { return events.saveAndFlush(newEvent(event)); }
    @Override public List<Event> findAllByTaskId(Long taskId) { return events.findAllByTaskId(taskId); }
    @Override public long count() { return events.count(); }
    @Override public List<Event> findAll() { return events.findAll(); }
    @Override public Optional<Event> findById(Long id) { return events.findById(id); }
    @Override public List<Event> findAllByEntityTypeAndEntityIdOrderByTimestamp(String entityType, Long entityId) {
        return events.findAllByEntityTypeAndEntityIdOrderByTimestamp(entityType, entityId);
    }
    @Override public void deleteAllForTest() {
        if (!environment.matchesProfiles("test")) throw new UnsupportedOperationException("Event deletion is test-only");
        events.deleteAll();
    }
    private Event newEvent(Event event) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Event appends require a writable caller transaction");
        }
        Objects.requireNonNull(event, "event must not be null");
        if (event.getId() != null) throw new IllegalArgumentException("Event Log entries are append-only");
        return event;
    }
}
