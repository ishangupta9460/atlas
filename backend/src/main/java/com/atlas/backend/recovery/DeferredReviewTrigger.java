package com.atlas.backend.recovery;

import com.atlas.backend.event.*;
import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Spec 05 section 8 permits implementation-tunable batch volume and cadence. */
@Service
public class DeferredReviewTrigger {
    public record Suggestion(int count, Instant proposedAt, String tier, String reason) {}
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final ExecutionClock clock;
    private final EventRepository events;
    private final int minimumCount, intervalDays;
    private final ObjectMapper mapper=new ObjectMapper();
    public DeferredReviewTrigger(JdbcTemplate db,SchedulingConfigRepository config,ExecutionClock clock,EventRepository events,
        @Value("${atlas.recovery.deferred-review.minimum-count:7}") int minimumCount,
        @Value("${atlas.recovery.deferred-review.interval-days:7}") int intervalDays) {
        if(minimumCount<1 || intervalDays<1) throw new IllegalArgumentException("Deferred review volume and cadence must be positive.");
        this.db=db;this.config=config;this.clock=clock;this.events=events;this.minimumCount=minimumCount;this.intervalDays=intervalDays;
    }
    private int count(Long owner) {
        return db.queryForObject("SELECT COUNT(*) FROM commitments WHERE user_id=? AND work_state='deferred'",Integer.class,owner);
    }
    @Transactional(readOnly=true)
    public Suggestion current(Long owner) {
        if(count(owner)<minimumCount) return null;
        var rows=db.query("SELECT payload FROM events WHERE entity_type='user' AND entity_id=? AND type='recovery.deferred_review' ORDER BY id DESC LIMIT 1",
            (r,n)->mapper.readValue(r.getString(1),Suggestion.class),owner);
        return rows.isEmpty() ? null : rows.get(0);
    }
    @Transactional
    public boolean propose(Long owner) {
        config.lockOwner(owner);
        int count=count(owner);if(count<minimumCount) return false;
        var prior=current(owner);
        if(prior!=null && clock.now().isBefore(prior.proposedAt().plus(Duration.ofDays(intervalDays)))) return false;
        var suggestion=new Suggestion(count,clock.now(),"COLLABORATIVE",
            "You have "+count+" deferred tasks. Review this batch when useful; nothing will be deleted or returned to planning without your choice.");
        events.appendAndFlush(Event.forEntity("user",owner,"recovery.deferred_review","atlas",suggestion.reason(),mapper.writeValueAsString(suggestion)));
        return true;
    }
}
