package com.atlas.backend.recurringintention;

import com.atlas.backend.event.*;
import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit weekly reconciliation, safe on every workspace refresh and across missed weeks. */
@Service
public class RecurringIntentionResetJob {
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final ExecutionClock clock;
    private final EventRepository events;
    public RecurringIntentionResetJob(JdbcTemplate db,SchedulingConfigRepository config,ExecutionClock clock,EventRepository events) {
        this.db=db; this.config=config; this.clock=clock; this.events=events;
    }
    @Transactional
    public List<Long> reconcile(Long owner) {
        config.lockOwner(owner);
        var hours=config.workingHours(owner); if(hours.isEmpty()) return List.of();
        ZoneId zone=ZoneId.of(hours.get().timezone());
        LocalDate week=clock.now().atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var ids=db.query("SELECT id FROM recurring_intentions WHERE user_id=? AND (reset_week_start IS NULL OR reset_week_start<?) ORDER BY id FOR UPDATE",(r,n)->r.getLong(1),owner,week);
        for(long id:ids) {
            // Reconcile completion facts from the current week; a delayed reset cannot erase them.
            int completed=db.queryForObject("""
                SELECT COUNT(*) FROM scheduled_blocks b LEFT JOIN actual_sessions a ON a.scheduled_block_id=b.id
                WHERE b.user_id=? AND b.recurring_intention_id=? AND b.start_time>=? AND b.start_time<?
                AND (a.completion_pct=100 OR (a.id IS NULL AND b.state='completed' AND EXISTS
                    (SELECT 1 FROM events e WHERE e.entity_type='scheduled_block' AND e.entity_id=b.id AND e.type='block.reported')))
                """,Integer.class,owner,id,utc(week.atStartOfDay(zone).toInstant()),utc(week.plusWeeks(1).atStartOfDay(zone).toInstant()));
            // Pre-execution manual completion reports have no ActualSession. Keep those facts too.
            var mapper=new tools.jackson.databind.ObjectMapper();
            var manual=db.query("SELECT timestamp,payload FROM events WHERE entity_type='recurring_intention' AND entity_id=? AND type='recurring_intention.instance_completed' ORDER BY id",(r,n)-> {
                String payload=r.getString(2);
                if(payload==null) return r.getTimestamp(1).toInstant(); // Legacy manual reports.
                var fact=mapper.readTree(payload);
                return fact.has("reportedAt") ? Instant.parse(fact.path("reportedAt").asText()) : null;
            },id);
            Instant weekStart=week.atStartOfDay(zone).toInstant(),weekEnd=week.plusWeeks(1).atStartOfDay(zone).toInstant();
            completed+=(int)manual.stream().filter(Objects::nonNull).filter(at->!at.isBefore(weekStart) && at.isBefore(weekEnd)).count();
            db.update("UPDATE recurring_intentions SET current_week_remaining_count=GREATEST(0,target_count_per_week-?),reset_week_start=? WHERE user_id=? AND id=?",completed,week,owner,id);
            events.appendAndFlush(Event.forEntity("recurring_intention",id,"recurring_intention.reset","atlas",
                "The weekly target starts in full without carrying missed-instance debt; current-week completions remain counted.","{\"week\":\""+week+"\"}"));
        }
        return List.copyOf(ids);
    }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
}
