package com.atlas.backend.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only projection of the existing log; ownership is checked before reading events. */
@Service
public class EventQueryService {
    private static final Map<String, String> OWNED_TABLES = Map.of(
        "goal", "goals", "commitment", "commitments", "task", "tasks",
        "recurring_intention", "recurring_intentions", "fixed_commitment", "fixed_commitments");
    private final JdbcTemplate jdbc;
    private final EventJpaRepository events;

    EventQueryService(JdbcTemplate jdbc, EventJpaRepository events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public record Item(Long id, String type, String entityType, Long entityId, String actor,
                       Instant timestamp, String reason, String payload, String description) {}
    public record Page(List<Item> events, Long nextCursor) {}

    @Transactional(readOnly = true)
    public Page recent(Long owner, String entityType, Long entityId, String actor, Long cursor, int limit) {
        String table = OWNED_TABLES.get(entityType);
        if (table == null || entityId == null || entityId <= 0 || limit < 1 || limit > 100
                || (cursor != null && cursor <= 0)
                || (actor != null && !List.of("atlas", "user").contains(actor))) {
            throw new IllegalArgumentException("Invalid event query");
        }
        // Table names come exclusively from the closed server-side allowlist above.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ? AND user_id = ?",
                Long.class, entityId, owner) == 0) {
            throw new EntityNotFound();
        }
        var rows = events.recent(entityType, entityId, actor, cursor == null ? Long.MAX_VALUE : cursor,
            PageRequest.of(0, limit + 1));
        var items = rows.stream().limit(limit).map(EventQueryService::render).toList();
        return new Page(items, rows.size() > limit ? items.get(items.size() - 1).id() : null);
    }

    static Item render(Event event) {
        String action = switch (event.getType()) {
            case "goal.at_risk" -> "marked this goal as at risk";
            case "goal.reactivated" -> "returned this goal to active planning";
            case "recurring_intention.reset" -> "reset this recurring intention for the new week";
            default -> "recorded a change to this " + event.getEntityType().replace('_', ' ');
        };
        String description = ("atlas".equals(event.getActor()) ? "Atlas " : "You ") + action + ".";
        if (event.getReason() != null && !event.getReason().isBlank()) {
            description += " " + event.getReason();
        }
        return new Item(event.getId(), event.getType(), event.getEntityType(), event.getEntityId(),
            event.getActor(), event.getTimestamp(), event.getReason(), event.getPayload(), description);
    }

    static final class EntityNotFound extends RuntimeException {}
}
