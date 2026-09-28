package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.recurringintention.RecurringIntentionResetJob;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Recurring source adapter into the existing Stage 0–8 planner and Problem B selection. */
@Service
public class RecurringSchedulingService {
    public record Input(Long recurringIntentionId,Integer workMinutes,String importance) {}
    public record Request(List<Input> work) {}
    public record Placed(long blockId,long recurringIntentionId,Instant startTime,Instant endTime) {}
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final SchedulingFoundationService foundation;
    private final RecurringIntentionResetJob reset;
    private final ExecutionClock clock;
    private final ExecutionIdempotency replay;
    private final EventRepository events;
    private final ObjectMapper mapper=new ObjectMapper();
    public RecurringSchedulingService(JdbcTemplate db,SchedulingConfigRepository config,SchedulingFoundationService foundation,
        RecurringIntentionResetJob reset,ExecutionClock clock,ExecutionIdempotency replay,EventRepository events) {
        this.db=db; this.config=config; this.foundation=foundation; this.reset=reset; this.clock=clock; this.replay=replay; this.events=events;
    }
    @Transactional
    public String generate(Long owner,String key,Request request) {
        if(request==null || request.work()==null || request.work().size()>1000 || request.work().stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Provide recurring work estimates and importance.");
        config.lockOwner(owner);
        String fingerprint="recurring-generation:"+mapper.writeValueAsString(request);
        String saved=replay.replay(owner,key,fingerprint); if(saved!=null) return saved;
        var hours=config.workingHours(owner).orElseThrow(()->new ExecutionException(409,"Configure working hours first."));
        ZoneId zone=ZoneId.of(hours.timezone()); Instant now=clock.now().truncatedTo(ChronoUnit.MICROS);
        LocalDate week=now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant end=week.plusWeeks(1).atStartOfDay(zone).toInstant();
        reset.reconcile(owner);
        var seen=new HashSet<Long>(); var candidates=new ArrayList<Commitment>(); var minutes=new TreeMap<Long,Integer>();
        var source=new HashMap<Long,Long>(); long sequence=-1;
        for(var input:request.work().stream().sorted(Comparator.comparing(Input::recurringIntentionId,Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
            if(input.recurringIntentionId()==null || !seen.add(input.recurringIntentionId()) || input.workMinutes()==null || input.workMinutes()<1 || input.workMinutes()>1440
                || input.importance()==null || !Commitment.IMPORTANCE.contains(input.importance())) throw new IllegalArgumentException("Provide distinct recurring IDs, work minutes 1–1440 and explicit importance.");
            var rows=db.query("SELECT r.*,g.lifecycle_state,g.planning_state FROM recurring_intentions r LEFT JOIN goals g ON g.id=r.goal_id WHERE r.user_id=? AND r.id=? FOR UPDATE",
                (r,n)->new Object[]{r.getObject("goal_id",Long.class),r.getObject("category_id",Long.class),r.getString("flexibility_tier"),r.getInt("current_week_remaining_count"),r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getString("lifecycle_state"),r.getString("planning_state")},owner,input.recurringIntentionId());
            if(rows.isEmpty()) throw new ExecutionException(404,"Recurring intention not found");
            var r=rows.get(0);
            if(r[0]!=null && (!"active".equals(r[5]) || !Set.of("active","at_risk").contains(r[6]))) continue;
            int existing=db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND recurring_intention_id=? AND state IN ('scheduled','active') AND start_time>=? AND start_time<?",Integer.class,
                owner,input.recurringIntentionId(),utc(week.atStartOfDay(zone).toInstant()),utc(end));
            for(int n=existing;n<(Integer)r[3];n++) {
                if(candidates.size()>=1000) throw new IllegalArgumentException("Generate at most 1000 instances at once.");
                long id=sequence--; candidates.add(Commitment.recurringSchedulingView(id,owner,(Long)r[0],(Long)r[1],input.importance(),(String)r[2],(Instant)r[4]));
                minutes.put(id,input.workMinutes()); source.put(id,input.recurringIntentionId());
            }
        }
        var risk=new HashSet<>(db.query("SELECT id FROM goals WHERE user_id=? AND planning_state='at_risk'",(r,n)->r.getLong(1),owner));
        var context=db.query("""
            SELECT b.id,b.start_time,b.end_time,COALESCE(c.category_id,ri.category_id) category_id FROM scheduled_blocks b
            LEFT JOIN commitments c ON c.id=b.commitment_id AND c.user_id=b.user_id
            LEFT JOIN recurring_intentions ri ON ri.id=b.recurring_intention_id AND ri.user_id=b.user_id
            WHERE b.user_id=? AND b.state IN ('scheduled','active','completed') AND b.start_time<? ORDER BY b.end_time,b.start_time,b.id
            """,(r,n)->new SlotSelection.Context(r.getObject(2,LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getObject(3,LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getObject(4,Long.class),r.getLong(1)),owner,utc(end));
        var plan=new SchedulingPlanner().plan(candidates,List.of(),risk,Set.of(),minutes,null,foundation.snapshot(owner,now,end),context);
        var placed=new ArrayList<Placed>();
        for(var p:plan.placements()) {
            var generated=new GeneratedKeyHolder();
            db.update(connection->{var s=connection.prepareStatement("INSERT INTO scheduled_blocks(user_id,recurring_intention_id,start_time,end_time,state,user_moved_flag,placement_reason) VALUES(?,?,?,?,'scheduled',FALSE,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
                s.setLong(1,owner);s.setLong(2,source.get(p.commitmentId()));s.setObject(3,utc(p.startTime()));s.setObject(4,utc(p.endTime()));s.setString(5,p.placementReason());return s;},generated);
            long id=Objects.requireNonNull(generated.getKey()).longValue();
            var b=new Placed(id,source.get(p.commitmentId()),p.startTime(),p.endTime()); placed.add(b);
            events.appendAndFlush(Event.forEntity("scheduled_block",id,"block.generated","atlas",p.placementReason(),mapper.writeValueAsString(b)));
        }
        return replay.save(owner,key,fingerprint,mapper.writeValueAsString(placed));
    }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
}
