package com.atlas.backend.event;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Repository
class JpaEventRepository implements EventRepository {
    private static final Logger log = LoggerFactory.getLogger(JpaEventRepository.class);
    private final EventJpaRepository events;
    private final Environment environment;
    JpaEventRepository(EventJpaRepository events, Environment environment) { this.events = events; this.environment = environment; }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Event append(Event event) { return events.save(tracedNewEvent(event)); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Event appendAndFlush(Event event) { return events.saveAndFlush(tracedNewEvent(event)); }
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

    private Event tracedNewEvent(Event event) {
        newEvent(event);
        log.atInfo().addKeyValue("operation", "event.append.requested")
            .addKeyValue("eventType", event.getType()).addKeyValue("entityType", event.getEntityType())
            .addKeyValue("entityId", event.getEntityId()).log("Event append requested");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                String outcome = switch (status) {
                    case STATUS_COMMITTED -> "committed";
                    case STATUS_ROLLED_BACK -> "rolled_back";
                    default -> "unknown";
                };
                log.atInfo().addKeyValue("operation", "event.transaction.completed")
                    .addKeyValue("eventType", event.getType())
                    .addKeyValue("entityId", event.getEntityId()).addKeyValue("outcome", outcome)
                    .log("Event transaction completed");
            }
        });
        return event;
    }
}
