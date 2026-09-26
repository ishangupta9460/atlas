package com.atlas.backend.execution;

import com.atlas.backend.commitment.*;
import com.atlas.backend.dependency.CommitmentDependencyRepository;
import com.atlas.backend.event.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** User-directed execution only. No autonomous ranking, placement or recovery. */
@Service
public class ExecutionService {
    private final JdbcTemplate db;
    private final ExecutionIdempotency idempotency;
    private final CommitmentRepository commitments;
    private final CommitmentService domain;
    private final CommitmentDependencyRepository dependencies;
    private final EventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();
    public ExecutionService(JdbcTemplate db, CommitmentRepository commitments, CommitmentService domain,
                            CommitmentDependencyRepository dependencies, EventRepository events, ExecutionIdempotency idempotency) {
        this.idempotency=idempotency;
        this.db=db; this.commitments=commitments; this.domain=domain; this.dependencies=dependencies; this.events=events;
    }
    public record Work(Long id, String title, String completionCriterion, String description, String workState,
        BigDecimal completionPct, String importance, String flexibilityTier, Instant deadline,
        Long goalId, String goalTitle, String goalState, String milestoneTitle, String categoryName, int blockers) {}
    public record Block(Long id, Long commitmentId, Instant startTime, Instant endTime, String state,
        String placementReason, String sessionState, Instant actualStart, Instant runningSince, long activeMillis) {}
    public record Fixed(Long id, String title, Instant startTime, Instant endTime) {}
    public record History(Long blockId, Long commitmentId, String title, Instant startTime, Instant endTime,
        long activeMillis, String report, BigDecimal completionPct) {}
    public record Workspace(Instant serverTime, List<Work> tasks, List<Block> blocks, List<Fixed> fixed, List<History> history) {}

    @Transactional(readOnly=true)
    public Workspace workspace(Long owner) {
        List<Work> work = db.query("""
            SELECT c.*, g.title goal_title, g.planning_state goal_state, m.title milestone_title, cat.name category_name,
              (SELECT COUNT(*) FROM commitment_dependency d JOIN commitments p ON p.id=d.blocking_commitment_id
               WHERE d.blocked_commitment_id=c.id AND p.work_state<>'completed') blockers
            FROM commitments c LEFT JOIN goals g ON g.id=c.goal_id
            LEFT JOIN milestones m ON m.id=c.milestone_id LEFT JOIN categories cat ON cat.id=c.category_id
            WHERE c.user_id=? ORDER BY c.created_at, c.id
            """, (r,n) -> new Work(r.getLong("id"),r.getString("title"),r.getString("completion_criterion"),
                r.getString("description"),r.getString("work_state"),r.getBigDecimal("current_completion_pct"),
                r.getString("importance"),r.getString("flexibility_tier"),instant(r,"own_deadline"),
                r.getObject("goal_id",Long.class),r.getString("goal_title"),r.getString("goal_state"),
                r.getString("milestone_title"),r.getString("category_name"),r.getInt("blockers")), owner);
        List<Block> blocks = db.query(blockSelect()+" WHERE b.user_id=? ORDER BY b.start_time,b.id", this::block, owner);
        List<Fixed> fixed = db.query("SELECT * FROM fixed_commitments WHERE user_id=? ORDER BY start_time,id",
            (r,n) -> new Fixed(r.getLong("id"),r.getString("title"),instant(r,"start_time"),instant(r,"end_time")),owner);
        List<History> history = db.query("""
            SELECT a.*,b.commitment_id,c.title FROM actual_sessions a
            JOIN scheduled_blocks b ON b.id=a.scheduled_block_id JOIN commitments c ON c.id=b.commitment_id
            WHERE b.user_id=? ORDER BY a.actual_end DESC,a.id DESC
            """, (r,n) -> new History(r.getLong("scheduled_block_id"),r.getLong("commitment_id"),r.getString("title"),
                instant(r,"actual_start"),instant(r,"actual_end"),r.getLong("active_millis"),
                r.getString("user_reported_outcome"),r.getBigDecimal("completion_pct")),owner);
        return new Workspace(Instant.now(),work,blocks,fixed,history);
    }

    @Transactional
    public String place(Long owner, String key, ExecutionController.Placement request) {
        return place(owner, key, request, "place:"+mapper.writeValueAsString(request));
    }
    @Transactional
    public String move(Long owner, Long id, String key, ExecutionController.Placement request) {
        lock(owner);
        String fingerprint="move:"+id+":"+mapper.writeValueAsString(request);
        String replay=replay(owner,key,fingerprint); if(replay!=null) return replay;
        Block old=owned(owner,id);
        if(!old.state().equals("scheduled") || !old.commitmentId().equals(request.commitmentId()))
            throw ExecutionException.conflict("Only an unstarted window for this task can move.");
        db.update("UPDATE scheduled_blocks SET state='superseded' WHERE id=?",id);
        String result=place(owner,key,request,fingerprint);
        long replacement=mapper.readTree(result).path("id").asLong();
        db.update("UPDATE scheduled_blocks SET superseded_by_block_id=? WHERE id=?",replacement,id);
        event(id,"block.superseded",Map.of("replacementBlockId",replacement),"You chose a new work window.");
        return result;
    }
    private String place(Long owner, String key, ExecutionController.Placement request, String fingerprint) {
        lock(owner);
        String replay=replay(owner,key,fingerprint); if (replay!=null) return replay;
        Commitment task=ready(owner,request.commitmentId());
        Instant start=request.startTime().truncatedTo(ChronoUnit.MICROS), end=request.endTime().truncatedTo(ChronoUnit.MICROS);
        if (!end.isAfter(start) || start.isBefore(Instant.now().minusSeconds(60)) ||
            start.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || end.isAfter(Instant.parse("9999-12-31T23:59:59Z")) ||
            Duration.between(start,end).compareTo(Duration.ofHours(24))>0)
            throw new ExecutionException(400,"Choose a future work window of at most 24 hours.");
        if (db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND commitment_id=? AND state IN ('scheduled','active')",Long.class,owner,task.getId())>0)
            throw ExecutionException.conflict("This task already has a work window. Continue from Today.");
        if (db.queryForObject("SELECT COUNT(*) FROM fixed_commitments WHERE user_id=? AND start_time<? AND end_time>?",Long.class,owner,utc(end),utc(start))>0 ||
            db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND state IN ('scheduled','active') AND start_time<? AND end_time>?",Long.class,owner,utc(end),utc(start))>0)
            throw ExecutionException.conflict("That window overlaps planned work. Choose another time; nothing has been moved.");
        GeneratedKeyHolder generated=new GeneratedKeyHolder();
        db.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO scheduled_blocks(user_id,commitment_id,start_time,end_time,state,user_moved_flag,placement_reason) VALUES(?,?,?,?,'scheduled',TRUE,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1,owner); statement.setLong(2,task.getId()); statement.setObject(3,utc(start)); statement.setObject(4,utc(end));
            statement.setString(5,"You chose this work window."); return statement;
        },generated);
        long id=Objects.requireNonNull(generated.getKey()).longValue();
        event(id,"block.placed",Map.of("commitmentId",task.getId(),"startTime",start.toString(),"endTime",end.toString()),"You chose this work window.");
        return save(owner,key,fingerprint,owned(owner,id));
    }

    @Transactional
    public String transition(Long owner, Long id, String action, String key, ExecutionController.Report report) {
        lock(owner);
        String fingerprint=id+":"+action+":"+mapper.writeValueAsString(report);
        String replay=replay(owner,key,fingerprint); if(replay!=null) return replay;
        Block b=owned(owner,id);
        Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (action.equals("start")) {
            if (!b.state().equals("scheduled") || b.sessionState()!=null) throw ExecutionException.conflict("This window has already been started.");
            if (now.isBefore(b.startTime()) || !now.isBefore(b.endTime())) throw ExecutionException.conflict("Start within the planned window. If it has passed, move it to a new time.");
            ready(owner,b.commitmentId());
            if (db.queryForObject("SELECT COUNT(*) FROM fixed_commitments WHERE user_id=? AND start_time<? AND end_time>?",Long.class,owner,utc(b.endTime()),utc(now))>0)
                throw ExecutionException.conflict("A fixed commitment now overlaps this window. Choose another time; nothing has been moved.");
            if (db.queryForObject("SELECT COUNT(*) FROM focus_sessions s JOIN scheduled_blocks b ON b.id=s.scheduled_block_id WHERE b.user_id=? AND s.state<>'finished'",Long.class,owner)>0)
                throw ExecutionException.conflict("Finish your current session before starting another task.");
            domain.transition(owner,b.commitmentId(),"in_progress",true,true);
            db.update("INSERT INTO focus_sessions(scheduled_block_id,state,actual_start,running_since,active_millis) VALUES(?,'running',?,?,0)",id,utc(now),utc(now));
            db.update("UPDATE scheduled_blocks SET state='active' WHERE id=?",id);
            event(id,"block.started",Map.of("at",now.toString()),null);
        } else {
            if (!b.state().equals("active") || b.sessionState()==null || b.sessionState().equals("finished"))
                throw ExecutionException.conflict("This session is no longer active. Refresh to continue.");
            long elapsed=b.activeMillis()+(b.runningSince()==null?0:Math.max(0,Duration.between(b.runningSince(),now).toMillis()));
            switch(action) {
                case "pause" -> {
                    if (!b.sessionState().equals("running")) throw ExecutionException.conflict("Only a running session can pause.");
                    db.update("UPDATE focus_sessions SET state='paused',running_since=NULL,active_millis=? WHERE scheduled_block_id=?",elapsed,id);
                }
                case "resume" -> {
                    if (!b.sessionState().equals("paused")) throw ExecutionException.conflict("Only a paused session can resume.");
                    db.update("UPDATE focus_sessions SET state='running',running_since=? WHERE scheduled_block_id=?",utc(now),id);
                }
                case "finish" -> {
                    if(report==null) throw new ExecutionException(400,"Tell Atlas what you accomplished.");
                    db.update("INSERT INTO actual_sessions(scheduled_block_id,actual_start,actual_end,active_millis,user_reported_outcome,completion_pct) VALUES(?,?,?,?,?,?)",
                        id,utc(b.actualStart()),utc(now),elapsed,report.report(),report.completionPct());
                    db.update("UPDATE focus_sessions SET state='finished',running_since=NULL,active_millis=? WHERE scheduled_block_id=?",elapsed,id);
                    db.update("UPDATE scheduled_blocks SET state='completed' WHERE id=?",id);
                    domain.reportExecution(owner,b.commitmentId(),report.completionPct());
                    event(id,"block.completed",Map.of("at",now.toString()),null);
                }
                default -> throw new ExecutionException(400,"Unknown session action");
            }
        }
        event(id,"session."+switch(action) { case "start" -> "started"; case "pause" -> "paused"; case "resume" -> "resumed"; default -> "finished"; },
            report==null ? Map.of("at",now.toString()) : Map.of("at",now.toString(),"report",report.report(),"completionPct",report.completionPct()),null);
        return save(owner,key,fingerprint,owned(owner,id));
    }

    private Commitment ready(Long owner, Long id) {
        Commitment c=commitments.lockOwned(id,owner).orElseThrow(CommitmentException::missing);
        if (!c.getWorkState().equals("ready")) throw ExecutionException.conflict("This task needs a title and completion criterion, and must be ready.");
        if (c.getGoalId()!=null && db.queryForObject("SELECT COUNT(*) FROM goals WHERE id=? AND user_id=? AND lifecycle_state='active' AND planning_state IN ('active','at_risk')",Long.class,c.getGoalId(),owner)==0)
            throw ExecutionException.conflict("Reactivate this goal before planning work on it.");
        if (db.queryForObject("SELECT COUNT(*) FROM commitment_dependency d JOIN commitments c ON c.id=d.blocking_commitment_id WHERE d.blocked_commitment_id=? AND c.work_state<>'completed'",Long.class,id)>0)
            throw ExecutionException.conflict("Finish the prerequisites before starting this task.");
        return c;
    }
    private void lock(Long owner) { if(dependencies.lockOwner(owner)==null) throw ExecutionException.missing(); }
    private String replay(Long owner,String key,String fingerprint) {
        return idempotency.replay(owner,key,fingerprint);
    }
    private String save(Long owner,String key,String fingerprint,Block b) {
        return idempotency.save(owner,key,fingerprint,mapper.writeValueAsString(b));
    }
    private void event(Long id,String type,Map<String,Object> payload,String reason) {
        events.appendAndFlush(Event.forEntity("scheduled_block",id,type,"user",reason,mapper.writeValueAsString(payload)));
    }
    private Block owned(Long owner,Long id) {
        return db.query(blockSelect()+" WHERE b.user_id=? AND b.id=?",this::block,owner,id).stream().findFirst().orElseThrow(ExecutionException::missing);
    }
    private String blockSelect() { return "SELECT b.*,s.state session_state,s.actual_start,s.running_since,s.active_millis FROM scheduled_blocks b LEFT JOIN focus_sessions s ON s.scheduled_block_id=b.id"; }
    private Block block(ResultSet r,int n) throws SQLException {
        return new Block(r.getLong("id"),r.getLong("commitment_id"),instant(r,"start_time"),instant(r,"end_time"),r.getString("state"),
            r.getString("placement_reason"),r.getString("session_state"),instant(r,"actual_start"),instant(r,"running_since"),r.getLong("active_millis"));
    }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
    private static Instant instant(ResultSet r,String column) throws SQLException {
        LocalDateTime value=r.getObject(column,LocalDateTime.class); return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
}
