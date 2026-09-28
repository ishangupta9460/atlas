package com.atlas.backend.recovery;

import com.atlas.backend.commitment.CommitmentRepository;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.goal.*;
import com.atlas.backend.scheduling.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoalRiskService {
    public record Estimate(Long commitmentId,Integer totalWorkMinutes,Integer localHour) {}
    public record RecurringEstimate(Long recurringIntentionId,Integer remainingWorkMinutes,Integer localHour) {}
    public record Request(List<Estimate> estimates,List<RecurringEstimate> recurringEstimates) {
        public Request(List<Estimate> estimates) { this(estimates,List.of()); }
    }
    public record Context(Long categoryId,int localHour,long instances,long completed,BigDecimal remainingMinutes) {}
    public record Snapshot(long id,long goalId,Instant calculatedAt,Instant horizonEnd,GoalRiskCalculator.Result calculation,
                           List<Context> contexts,Request inputs,boolean awaitingResponse,String planningState) {}
    public record Response(String option,String note,LocalDate deadline,Request estimates) {}
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final SchedulingFoundationService foundation;
    private final CommitmentRepository commitments;
    private final GoalService goals;
    private final ExecutionClock clock;
    private final ExecutionIdempotency idempotency;
    private final EventRepository events;
    private final BigDecimal threshold;
    private final ContextualCompletionEvidence history;
    private final ObjectMapper mapper=new ObjectMapper();
    public GoalRiskService(JdbcTemplate db,SchedulingConfigRepository config,SchedulingFoundationService foundation,
                          CommitmentRepository commitments,GoalService goals,ExecutionClock clock,ExecutionIdempotency idempotency,
                          EventRepository events,ContextualCompletionEvidence history,@Value("${atlas.recovery.confidence-threshold:0.80}") BigDecimal threshold) {
        GoalRiskCalculator.calculate(BigDecimal.ZERO,BigDecimal.ZERO,null,threshold);
        this.db=db; this.config=config; this.foundation=foundation; this.commitments=commitments; this.goals=goals;
        this.clock=clock; this.idempotency=idempotency; this.events=events; this.threshold=threshold; this.history=history;
    }
    @Transactional(readOnly=true)
    public Snapshot get(Long owner,long goal) {
        goals.get(owner,goal);
        var rows=db.query("SELECT id,evidence_json FROM goal_risk_snapshots WHERE goal_id=? ORDER BY id DESC LIMIT 1",(r,n)->new Object[]{r.getLong(1),r.getString(2)},goal);
        if(rows.isEmpty()) return null;
        var s=mapper.readValue((String)rows.get(0)[1],Snapshot.class);
        return new Snapshot((Long)rows.get(0)[0],s.goalId(),s.calculatedAt(),s.horizonEnd(),s.calculation(),s.contexts(),s.inputs(),pending(goal),goals.get(owner,goal).planningState());
    }
    @Transactional
    public String evaluate(Long owner,long goal,String key,Request request) {
        config.lockOwner(owner);
        String fingerprint="risk:"+goal+":"+mapper.writeValueAsString(request);
        String saved=idempotency.replay(owner,key,fingerprint); if(saved!=null) return saved;
        return idempotency.save(owner,key,fingerprint,mapper.writeValueAsString(calculate(owner,goal,request,false,false)));
    }
    @Transactional
    public String respond(Long owner,long goal,String key,Response response) {
        config.lockOwner(owner);
        goals.get(owner,goal);
        if(response==null || response.option()==null || !Set.of("increase_effort","extend_deadline","reduce_scope","change_method","defer_pause").contains(response.option())
            || response.note()==null || response.note().isBlank() || response.note().length()>8000)
            throw new IllegalArgumentException("Choose one of the five options and describe your change.");
        String fingerprint="risk-response:"+goal+":"+mapper.writeValueAsString(response);
        String saved=idempotency.replay(owner,key,fingerprint); if(saved!=null) return saved;
        var previous=get(owner,goal);
        if(previous==null || (!previous.awaitingResponse() && !previous.planningState().equals("at_risk")))
            throw new ExecutionException(409,"There is no active risk review for this goal.");
        db.queryForObject("SELECT id FROM goals WHERE id=? AND user_id=? FOR UPDATE",Long.class,goal,owner);
        var g=goals.get(owner,goal);
        if(!g.lifecycleState().equals("active") || g.planningState().equals("paused")) throw new ExecutionException(409,"This goal is not in active planning.");
        boolean firstTransition=!g.planningState().equals("at_risk");
        if(firstTransition) goals.markAtRisk(owner,goal);
        if(response.option().equals("extend_deadline")) {
            if(response.deadline()==null || (g.targetDeadline()!=null && !response.deadline().isAfter(g.targetDeadline())))
                throw new IllegalArgumentException("Provide a later deadline.");
            var update=new UpdateGoalRequest(); update.setTargetDeadline(response.deadline()); goals.update(owner,goal,update);
        }
        Snapshot snapshot;
        if(response.option().equals("defer_pause")) {
            goals.pause(owner,goal);
            db.update("UPDATE goal_risk_reviews SET awaiting_response=FALSE WHERE goal_id=?",goal);
            snapshot=get(owner,goal);
        } else {
            snapshot=calculate(owner,goal,response.estimates()==null ? previous.inputs() : response.estimates(),true,firstTransition);
            if(snapshot.calculation().result().equals("feasible")) {
                goals.resolveRisk(owner,goal);
                db.update("UPDATE goal_risk_reviews SET awaiting_response=FALSE WHERE goal_id=?",goal);
                snapshot=get(owner,goal);
            }
        }
        events.appendAndFlush(Event.forEntity("goal",goal,"goal.risk_response","user",null,mapper.writeValueAsString(response)));
        return idempotency.save(owner,key,fingerprint,mapper.writeValueAsString(snapshot));
    }
    private Snapshot calculate(Long owner,long goal,Request request,boolean responding,boolean firstTransition) {
        goals.get(owner,goal);
        db.queryForObject("SELECT id FROM goals WHERE id=? AND user_id=? FOR UPDATE",Long.class,goal,owner);
        var g=goals.get(owner,goal);
        if(!g.lifecycleState().equals("active") || !Set.of("active","deferred","at_risk").contains(g.planningState()))
            throw new ExecutionException(409,"Risk evaluation requires an active goal.");
        var hours=config.workingHours(owner).orElseThrow(()->new ExecutionException(409,"Configure a scheduling timezone first."));
        if(g.targetDeadline()==null) throw new ExecutionException(409,"Set a goal deadline before evaluating deadline feasibility.");
        ZoneId zone=ZoneId.of(hours.timezone()); Instant now=clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant end=g.targetDeadline().plusDays(1).atStartOfDay(zone).toInstant();
        if(request==null || request.estimates()==null || request.estimates().size()>1000) throw new IllegalArgumentException("Provide the incomplete tasks' total estimates and intended local hours.");
        var estimates=new TreeMap<Long,Estimate>();
        for(var e:request.estimates()) if(e==null || e.commitmentId()==null || e.totalWorkMinutes()==null || e.totalWorkMinutes()<1
            || e.localHour()==null || e.localHour()<0 || e.localHour()>23 || estimates.put(e.commitmentId(),e)!=null)
            throw new IllegalArgumentException("Provide distinct tasks, positive estimates and local hours 0–23.");
        var work=commitments.lockAllOwned(owner).stream().filter(c->Objects.equals(c.getGoalId(),goal) && !Set.of("completed","cancelled").contains(c.getWorkState())).toList();
        if(!estimates.keySet().equals(work.stream().map(c->c.getId()).collect(java.util.stream.Collectors.toSet())))
            throw new ExecutionException(400,"Estimates must cover exactly this goal's incomplete owned tasks.");
        var facts=history.read(owner,now);
        BigDecimal remaining=BigDecimal.ZERO,adjustedWork=BigDecimal.ZERO;
        boolean missing=false,zeroRate=false;
        var contexts=new ArrayList<Context>();
        for(var c:work) {
            var e=estimates.get(c.getId());
            var rem=BigDecimal.valueOf(e.totalWorkMinutes()).multiply(BigDecimal.valueOf(100).subtract(c.getCurrentCompletionPct())).divide(BigDecimal.valueOf(100));
            var relevant=facts.stream().filter(f->Objects.equals(f.categoryId(),c.getCategoryId()) && f.start().atZone(zone).getHour()==e.localHour()).toList();
            long completed=relevant.stream().filter(ContextualCompletionEvidence.Fact::completed).count();
            contexts.add(new Context(c.getCategoryId(),e.localHour(),relevant.size(),completed,rem)); remaining=remaining.add(rem);
            if(rem.signum()>0) {
                if(relevant.isEmpty()) missing=true;
                else if(completed==0) zeroRate=true;
                else adjustedWork=adjustedWork.add(rem.multiply(BigDecimal.valueOf(relevant.size())).divide(BigDecimal.valueOf(completed),12,RoundingMode.HALF_EVEN));
            }
        }
        var recurring=db.query("SELECT id,category_id FROM recurring_intentions WHERE user_id=? AND goal_id=? ORDER BY id FOR UPDATE",
            (r,n)->new Long[]{r.getLong(1),r.getObject(2,Long.class)},owner,goal);
        var recurringInputs=new TreeMap<Long,RecurringEstimate>();
        if(request.recurringEstimates()!=null) for(var e:request.recurringEstimates()) {
            if(e==null || e.recurringIntentionId()==null || e.remainingWorkMinutes()==null || e.remainingWorkMinutes()<0
                || e.localHour()==null || e.localHour()<0 || e.localHour()>23 || recurringInputs.put(e.recurringIntentionId(),e)!=null)
                throw new IllegalArgumentException("Provide distinct recurring intentions with explicit remaining effort to the deadline and local hours.");
        }
        if(!recurringInputs.keySet().equals(recurring.stream().map(r->r[0]).collect(java.util.stream.Collectors.toSet())))
            throw new ExecutionException(400,"Include remaining effort estimates for every recurring intention linked to this goal.");
        for(var ri:recurring) {
            var e=recurringInputs.get(ri[0]); var rem=BigDecimal.valueOf(e.remainingWorkMinutes());
            var relevant=facts.stream().filter(f->Objects.equals(f.recurringIntentionId(),ri[0]) && f.start().atZone(zone).getHour()==e.localHour()).toList();
            long completed=relevant.stream().filter(ContextualCompletionEvidence.Fact::completed).count();
            contexts.add(new Context(ri[1],e.localHour(),relevant.size(),completed,rem)); remaining=remaining.add(rem);
            if(rem.signum()>0) {
                if(relevant.isEmpty()) missing=true;
                else if(completed==0) zeroRate=true;
                else adjustedWork=adjustedWork.add(rem.multiply(BigDecimal.valueOf(relevant.size())).divide(BigDecimal.valueOf(completed),12,RoundingMode.HALF_EVEN));
            }
        }
        BigDecimal capacity=BigDecimal.ZERO;
        for(Instant start=now;start.isBefore(end);) {
            Instant stop=start.atZone(zone).toLocalDate().plusDays(30).atStartOfDay(zone).toInstant(); if(stop.isAfter(end)) stop=end;
            var calendar=foundation.snapshot(owner,start,stop);
            // Include capacity already reserved for this goal's scheduled work; do not subtract it twice.
            var otherBlocks=db.query("""
                SELECT b.start_time,CASE WHEN b.state='active' AND COALESCE(c.goal_id,ri.goal_id,-1)=? AND b.end_time>? THEN ? ELSE b.end_time END FROM scheduled_blocks b
                LEFT JOIN commitments c ON c.id=b.commitment_id LEFT JOIN recurring_intentions ri ON ri.id=b.recurring_intention_id
                WHERE b.user_id=? AND b.state IN ('scheduled','active','completed')
                AND NOT (b.state='scheduled' AND COALESCE(c.goal_id,ri.goal_id,-1)=?) ORDER BY b.start_time,b.id
                """,(r,n)-> {
                    Instant a=r.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),b=r.getObject(2,LocalDateTime.class).toInstant(ZoneOffset.UTC);
                    return b.isAfter(a) ? new TimeInterval(a,b) : null;
                },goal,utc(now),utc(now),owner,goal).stream().filter(Objects::nonNull).toList();
            calendar=new SchedulingFoundationService.Snapshot(calendar.hours(),calendar.start(),calendar.end(),calendar.policy(),calendar.fixed(),otherBlocks,calendar.expanded());
            long seconds=calendar.calculate(1,List.of()).days().stream().mapToLong(d->d.capacity().remainingWorkSeconds()).sum();
            capacity=capacity.add(BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60),6,RoundingMode.HALF_EVEN)); start=stop;
        }
        if(missing && remaining.signum()>0 && capacity.signum()>0)
            throw new ExecutionException(409,"Goal Risk is unavailable: contextual completion evidence is missing. No historical rate or confidence has been invented.");
        BigDecimal rate=remaining.signum()==0 ? BigDecimal.ONE : zeroRate ? BigDecimal.ZERO : adjustedWork.signum()==0 ? null : remaining.divide(adjustedWork,12,RoundingMode.HALF_EVEN);
        var result=GoalRiskCalculator.calculate(remaining,capacity,rate,threshold);
        boolean risk=result.result().equals("at_risk"); boolean pending=pending(goal) || risk;
        var generated=new GeneratedKeyHolder();
        var normalized=new Request(List.copyOf(estimates.values()),List.copyOf(recurringInputs.values()));
        // Snapshot evidence is written once, never updated, including response-triggered recalculations.
        var evidence=new Snapshot(0,goal,now,end,result,List.copyOf(contexts),normalized,pending,g.planningState());
        db.update(connection -> {
            var s=connection.prepareStatement("INSERT INTO goal_risk_snapshots(goal_id,calculated_at,remaining_work_estimate,available_capacity_estimate,context_adjusted_completion_rate,confidence,confidence_threshold,result,triggered_transition,evidence_json) VALUES(?,?,?,?,?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1,goal); s.setObject(2,utc(now)); s.setBigDecimal(3,result.remainingMinutes()); s.setBigDecimal(4,result.capacityMinutes());
            s.setBigDecimal(5,result.completionRate()); s.setBigDecimal(6,result.confidence()); s.setBigDecimal(7,threshold); s.setString(8,result.result());
            s.setBoolean(9,firstTransition || (responding && g.planningState().equals("at_risk") && result.result().equals("feasible"))); s.setString(10,mapper.writeValueAsString(evidence)); return s;
        },generated);
        long id=Objects.requireNonNull(generated.getKey()).longValue();
        if(db.queryForObject("SELECT COUNT(*) FROM goal_risk_reviews WHERE goal_id=?",Integer.class,goal)==0)
            db.update("INSERT INTO goal_risk_reviews(goal_id,snapshot_id,awaiting_response) VALUES(?,?,?)",goal,id,pending);
        else db.update("UPDATE goal_risk_reviews SET snapshot_id=?,awaiting_response=? WHERE goal_id=?",id,pending,goal);
        events.appendAndFlush(Event.forEntity("goal",goal,"goal.risk_evaluated","atlas",
            "Confidence below the configured "+threshold.toPlainString()+" threshold is At Risk; equality is feasible. Missing contextual history prevents an evidence-based evaluation.",mapper.writeValueAsString(evidence)));
        return new Snapshot(id,goal,now,end,result,contexts,normalized,pending,g.planningState());
    }
    private boolean pending(long goal) { return db.query("SELECT awaiting_response FROM goal_risk_reviews WHERE goal_id=?",(r,n)->r.getBoolean(1),goal).stream().findFirst().orElse(false); }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
}
