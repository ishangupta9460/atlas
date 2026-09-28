package com.atlas.backend.recovery;

import com.atlas.backend.commitment.*;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RetrospectiveReportService {
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final CommitmentService commitments;
    private final ExecutionIdempotency replay;
    private final EventRepository events;
    private final ExecutionClock clock;
    private final com.atlas.backend.recurringintention.RecurringIntentionResetJob resets;
    private final ObjectMapper mapper=new ObjectMapper();
    public RetrospectiveReportService(JdbcTemplate db, SchedulingConfigRepository config, CommitmentService commitments,
                                     ExecutionIdempotency replay, EventRepository events, ExecutionClock clock,com.atlas.backend.recurringintention.RecurringIntentionResetJob resets) {
        this.db=db; this.config=config; this.commitments=commitments; this.replay=replay; this.events=events; this.clock=clock;this.resets=resets;
    }
    public record Report(String outcome, String report, BigDecimal completionPct) {}
    @Transactional
    public String report(Long owner, Long id, String key, Report report) {
        if(report==null || report.outcome()==null || !Set.of("completed","partial","skipped").contains(report.outcome())
            || report.report()==null || report.report().isBlank() || report.report().length()>8000
            || report.completionPct()==null || report.completionPct().scale()>2 || report.completionPct().signum()<0
            || report.completionPct().compareTo(BigDecimal.valueOf(100))>0
            || (report.outcome().equals("completed") != (report.completionPct().compareTo(BigDecimal.valueOf(100))==0)))
            throw new IllegalArgumentException("Provide a completed, partial or skipped report and a matching completion percentage.");
        config.lockOwner(owner);
        String fingerprint="retro:"+id+":"+mapper.writeValueAsString(report);
        String saved=replay.replay(owner,key,fingerprint); if(saved!=null) return saved;
        var rows=db.query("""
            SELECT b.commitment_id,b.state,b.end_time,b.recurring_intention_id,b.start_time FROM scheduled_blocks b
            WHERE b.user_id=? AND b.id=? AND NOT EXISTS (SELECT 1 FROM focus_sessions f WHERE f.scheduled_block_id=b.id)
            AND NOT EXISTS (SELECT 1 FROM actual_sessions a WHERE a.scheduled_block_id=b.id)
            """,(r,n)->new Object[]{r.getObject(1,Long.class),r.getString(2),r.getObject(3,LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getObject(4,Long.class),r.getObject(5,LocalDateTime.class).toInstant(ZoneOffset.UTC)},owner,id);
        if(rows.isEmpty()) throw new ExecutionException(404,"Block not found");
        var row=rows.get(0); Long task=(Long)row[0]; String state=(String)row[1];
        if(!Set.of("scheduled","unresolved").contains(state) || clock.now().isBefore((Instant)row[2]))
            throw new ExecutionException(409,"This window cannot accept a retrospective report. Refresh its recovery status.");
        if(db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='scheduled_block' AND entity_id=? AND type='block.reported'",Long.class,id)>0)
            throw new ExecutionException(409,"This window already has a report. Use progress correction to revise your current belief.");
        if(db.queryForObject("SELECT COUNT(*) FROM recovery_block_claims WHERE scheduled_block_id=?",Long.class,id)>0)
            throw new ExecutionException(409,"Review the existing recovery decision before reporting this window.");
        if(task!=null) {
            var current=commitments.get(owner,task);
            if(report.outcome().equals("skipped") && current.currentCompletionPct().compareTo(report.completionPct())!=0)
                throw new IllegalArgumentException("A skipped window preserves existing completion progress.");
            if(!report.outcome().equals("skipped")) commitments.reportRetrospectively(owner,task,report.completionPct());
        } else {
            resets.reconcile(owner);
            var hours=config.workingHours(owner).orElseThrow(()->new ExecutionException(409,"Configure a scheduling timezone first."));
            var zone=ZoneId.of(hours.timezone());
            var week=clock.now().atZone(zone).toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if(report.outcome().equals("completed") && !((Instant)row[4]).atZone(zone).toLocalDate().isBefore(week))
                db.update("UPDATE recurring_intentions SET current_week_remaining_count=GREATEST(0,current_week_remaining_count-1) WHERE user_id=? AND id=?",owner,row[3]);
            String eventType=switch(report.outcome()) {
                case "completed" -> "recurring_intention.instance_completed";
                case "skipped" -> "recurring_intention.instance_missed";
                default -> "recurring_intention.instance_partial";
            };
            events.appendAndFlush(Event.forEntity("recurring_intention",(Long)row[3],eventType,"user",null,mapper.writeValueAsString(Map.of("scheduledBlockId",id,"outcome",report.outcome()))));
        }
        db.update("UPDATE scheduled_blocks SET state=? WHERE id=? AND user_id=?",report.outcome().equals("completed") ? "completed" : "unresolved",id,owner);
        events.appendAndFlush(Event.forEntity("scheduled_block",id,"block.reported","user",null,mapper.writeValueAsString(report)));
        return replay.save(owner,key,fingerprint,mapper.writeValueAsString(Map.of("blockId",id,"outcome",report.outcome(),"completionPct",report.completionPct())));
    }
}
