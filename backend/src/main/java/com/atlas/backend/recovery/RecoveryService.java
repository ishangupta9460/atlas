package com.atlas.backend.recovery;

import com.atlas.backend.commitment.*;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.scheduling.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecoveryService {
    public record Item(Long blockId, Integer totalWorkMinutes) {}
    public record Request(List<Item> items, Instant unavailableStart, Instant unavailableEnd) {
        public Request(List<Item> items) { this(items,null,null); }
    }
    public record Proposal(DecisionTierClassifier.Tier tier, SchedulingPlanner.Plan plan, List<Long> deferred, String reason) {}
    public record Decision(long id, String state, Proposal proposal, List<SchedulingPipeline.Placed> blocks) {}
    private record Source(long id, long taskId, String state, boolean sticky, Instant start, Instant end) {}
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final CommitmentRepository commitments;
    private final CommitmentService domain;
    private final ProgressiveSlotSearcher search;
    private final SchedulingPipeline pipeline;
    private final ExecutionIdempotency idempotency;
    private final ExecutionClock clock;
    private final EventRepository events;
    private final ObjectMapper mapper=new ObjectMapper();
    public RecoveryService(JdbcTemplate db, SchedulingConfigRepository config, CommitmentRepository commitments,
                           CommitmentService domain, ProgressiveSlotSearcher search, SchedulingPipeline pipeline,
                           ExecutionIdempotency idempotency, ExecutionClock clock, EventRepository events) {
        this.db=db; this.config=config; this.commitments=commitments; this.domain=domain; this.search=search;
        this.pipeline=pipeline; this.idempotency=idempotency; this.clock=clock; this.events=events;
    }
    @Transactional
    public String recover(Long owner, String key, Request input) {
        Request request=validate(input);
        config.lockOwner(owner);
        String fingerprint="recover:"+mapper.writeValueAsString(request);
        String saved=idempotency.replay(owner,key,fingerprint); if(saved!=null) return saved;
        for(var item:request.items()) if(db.queryForObject("SELECT COUNT(*) FROM recovery_block_claims c JOIN scheduled_blocks b ON b.id=c.scheduled_block_id WHERE b.user_id=? AND c.scheduled_block_id=?",Long.class,owner,item.blockId())>0)
            throw new ExecutionException(409,"This window already has a recovery decision.");
        Proposal proposal=propose(owner,request);
        var generated=new GeneratedKeyHolder();
        db.update(connection -> {
            var s=connection.prepareStatement("INSERT INTO recovery_decisions(user_id,tier,state,request_json,proposal_json,reason,created_at) VALUES(?,?,'pending',?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            s.setLong(1,owner); s.setString(2,proposal.tier().name()); s.setString(3,mapper.writeValueAsString(request));
            s.setString(4,mapper.writeValueAsString(proposal)); s.setString(5,proposal.reason()); s.setObject(6,utc(clock.now())); return s;
        },generated);
        long id=Objects.requireNonNull(generated.getKey()).longValue();
        for(var item:request.items()) db.update("INSERT INTO recovery_block_claims(scheduled_block_id,decision_id) VALUES(?,?)",item.blockId(),id);
        event(request,"recovery.proposed",proposal);
        List<SchedulingPipeline.Placed> blocks=List.of(); String state="pending";
        if(proposal.tier()==DecisionTierClassifier.Tier.AUTONOMOUS) { blocks=apply(owner,id,request,proposal); state="applied"; }
        return idempotency.save(owner,key,fingerprint,mapper.writeValueAsString(new Decision(id,state,proposal,blocks)));
    }
    @Transactional
    public String respond(Long owner, long id, String key, boolean accept) {
        config.lockOwner(owner);
        String fingerprint="recovery-response:"+id+":"+accept;
        String saved=idempotency.replay(owner,key,fingerprint); if(saved!=null) return saved;
        var rows=db.query("SELECT state,request_json,proposal_json FROM recovery_decisions WHERE id=? AND user_id=?",(r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},id,owner);
        if(rows.isEmpty()) throw new ExecutionException(404,"Recovery decision not found");
        var row=rows.get(0);
        if(!row[0].equals("pending")) throw new ExecutionException(409,"This decision is no longer pending.");
        var request=mapper.readValue(row[1],Request.class); var proposal=mapper.readValue(row[2],Proposal.class);
        List<SchedulingPipeline.Placed> blocks=List.of();
        if(accept) {
            var fresh=propose(owner,request);
            // Revalidate under the shared owner lock: never apply stale capacity, metadata or progress.
            if(fresh.tier()!=proposal.tier() || !fresh.deferred().equals(proposal.deferred())
                || !placementShape(fresh).equals(placementShape(proposal)))
                throw new ExecutionException(409,"The schedule or progress changed. Dismiss this proposal and request a fresh recovery review.");
            proposal=fresh;
            if(proposal.plan().placements().isEmpty() && proposal.deferred().isEmpty())
                throw new ExecutionException(409,"There is no valid change to apply. Update capacity or constraints first.");
            blocks=apply(owner,id,request,proposal);
        } else {
            db.update("UPDATE recovery_decisions SET state='dismissed' WHERE id=? AND user_id=?",id,owner);
            db.update("DELETE FROM recovery_block_claims WHERE decision_id=?",id);
            event(request,"recovery.dismissed",proposal);
        }
        return idempotency.save(owner,key,fingerprint,mapper.writeValueAsString(new Decision(id,accept ? "applied" : "dismissed",proposal,blocks)));
    }
    private Proposal propose(Long owner, Request request) {
        var hours=config.workingHours(owner).orElseThrow(()->new ExecutionException(409,"Configure your scheduling timezone and working hours first."));
        ZoneId zone=ZoneId.of(hours.timezone()); Instant now=clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Map<Long,Commitment> tasks=new TreeMap<>(); commitments.lockAllOwned(owner).forEach(c->tasks.put(c.getId(),c));
        var inputs=new ArrayList<SchedulingPipeline.WorkInput>(); var sources=new ArrayList<Source>(); var selected=new HashSet<Long>();
        var excluded=new HashSet<Long>();
        for(var item:request.items()) {
            var source=source(owner,item.blockId()); var c=tasks.get(source.taskId());
            if(c==null || !c.getWorkState().equals("ready") || !selected.add(c.getId())
                || !(Set.of("unresolved","completed").contains(source.state()) || (source.state().equals("scheduled")
                    && request.unavailableStart()!=null && source.start().isBefore(request.unavailableEnd()) && source.end().isAfter(request.unavailableStart()))))
                throw new ExecutionException(409,"Recover one unresolved or partially completed window per ready task.");
            if(source.state().equals("completed") && c.getCurrentCompletionPct().compareTo(BigDecimal.valueOf(100))>=0)
                throw new ExecutionException(409,"Completed work has no recoverable remainder.");
            if(db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND commitment_id=? AND id<>? AND state IN ('active','scheduled')",Long.class,owner,c.getId(),source.id())>0)
                throw new ExecutionException(409,"This task already has an active or scheduled window.");
            int minutes=BigDecimal.valueOf(item.totalWorkMinutes()).multiply(BigDecimal.valueOf(100).subtract(c.getCurrentCompletionPct()))
                .divide(BigDecimal.valueOf(100),0,RoundingMode.CEILING).intValueExact();
            if(minutes<1) throw new ExecutionException(409,"There is no remaining work.");
            inputs.add(new SchedulingPipeline.WorkInput(c.getId(),minutes)); sources.add(source);
            if(source.state().equals("scheduled")) excluded.add(source.id());
        }
        var plan=search.search(owner,now,zone,inputs,excluded,request.unavailableStart()==null ? List.of() : List.of(new TimeInterval(request.unavailableStart(),request.unavailableEnd())));
        var risk=new HashSet<>(db.query("SELECT id FROM goals WHERE user_id=? AND planning_state='at_risk'",(r,n)->r.getLong(1),owner));
        var work=selected.stream().map(tasks::get).toList();
        var latest=plan.placements().stream().map(p->p.startTime().atZone(zone).toLocalDate()).max(Comparator.naturalOrder()).orElse(null);
        var tier=DecisionTierClassifier.classify(new DecisionTierClassifier.Touches(work.size(),
            work.stream().allMatch(c->c.getFlexibilityTier().equals("flexible")),
            work.stream().anyMatch(c->Set.of("fixed","protected").contains(c.getFlexibilityTier())),
            sources.stream().anyMatch(Source::sticky),work.stream().anyMatch(c->Set.of("high","critical").contains(c.getImportance())),
            work.stream().anyMatch(c->risk.contains(c.getGoalId())),!plan.importantTies().isEmpty(),false,false,false,
            plan.placements().isEmpty(),false,now.atZone(zone).toLocalDate(),latest));
        String reason="Searched suitable capacity today, tomorrow, then the rest of this week using the scheduling hierarchy. "
            +(plan.unplaced().isEmpty() ? "All remaining work fits." : "Some remaining work has no suitable placement.");
        return new Proposal(tier,plan,OverloadResolver.deferred(plan,tasks),reason);
    }
    private List<SchedulingPipeline.Placed> apply(Long owner,long id,Request request,Proposal proposal) {
        var blocks=pipeline.persist(owner,proposal.plan());
        for(var item:request.items()) {
            var source=source(owner,item.blockId());
            var replacement=blocks.stream().filter(b->b.decision().commitmentId()==source.taskId()).findFirst();
            if(replacement.isPresent()) {
                db.update("UPDATE scheduled_blocks SET superseded_by_block_id=?,state=? WHERE id=? AND user_id=?",replacement.get().id(),source.state().equals("completed") ? "completed" : "superseded",source.id(),owner);
                events.appendAndFlush(Event.forEntity("scheduled_block",source.id(),"block.superseded","atlas",proposal.reason(),mapper.writeValueAsString(replacement.get())));
            } else if(proposal.deferred().contains(source.taskId()) && source.state().equals("scheduled")) {
                db.update("UPDATE scheduled_blocks SET state='unresolved' WHERE id=? AND user_id=?",source.id(),owner);
                events.appendAndFlush(Event.forEntity("scheduled_block",source.id(),"block.unresolved","atlas","The reported interruption removed this window; its work remains deferred."));
            }
        }
        for(long task:proposal.deferred()) domain.recoveryDeferral(owner,task,true,"No suitable recovery capacity remained in the progressive search.");
        db.update("UPDATE recovery_decisions SET state='applied' WHERE id=? AND user_id=?",id,owner);
        event(request,"recovery.applied",proposal);
        return blocks;
    }
    private Source source(Long owner,long id) {
        return db.query("SELECT id,commitment_id,state,user_moved_flag,start_time,end_time FROM scheduled_blocks WHERE id=? AND user_id=? AND commitment_id IS NOT NULL",
            (r,n)->new Source(r.getLong(1),r.getLong(2),r.getString(3),r.getBoolean(4),r.getObject(5,LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getObject(6,LocalDateTime.class).toInstant(ZoneOffset.UTC)),id,owner)
            .stream().findFirst().orElseThrow(()->new ExecutionException(404,"Block not found"));
    }
    private void event(Request request,String type,Proposal proposal) {
        for(var item:request.items()) events.appendAndFlush(Event.forEntity("scheduled_block",item.blockId(),type,"atlas",proposal.reason(),mapper.writeValueAsString(proposal)));
    }
    private Request validate(Request r) {
        if(r==null || r.items()==null || r.items().isEmpty() || r.items().size()>1000) throw new IllegalArgumentException("Provide 1–1000 recovery items.");
        var ids=new HashSet<Long>();
        for(var item:r.items()) if(item==null || item.blockId()==null || item.blockId()<1 || !ids.add(item.blockId())
            || item.totalWorkMinutes()==null || item.totalWorkMinutes()<1 || item.totalWorkMinutes()>1440)
            throw new IllegalArgumentException("Provide distinct block IDs and explicit total work estimates of 1–1440 minutes.");
        if((r.unavailableStart()==null)!=(r.unavailableEnd()==null) || (r.unavailableStart()!=null && !r.unavailableEnd().isAfter(r.unavailableStart())))
            throw new IllegalArgumentException("Provide a positive interruption interval.");
        return new Request(r.items().stream().sorted(Comparator.comparing(Item::blockId)).toList(),r.unavailableStart(),r.unavailableEnd());
    }
    private Map<Long,String> placementShape(Proposal p) {
        var shape=new TreeMap<Long,String>();
        for(var b:p.plan().placements()) shape.put(b.commitmentId(),Duration.between(b.startTime(),b.endTime()).toString());
        return shape;
    }
    private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant,ZoneOffset.UTC); }
}
