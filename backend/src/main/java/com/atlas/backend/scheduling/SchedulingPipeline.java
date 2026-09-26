package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.CommitmentRepository;
import com.atlas.backend.dependency.CommitmentDependencyRepository;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SchedulingPipeline {
    private final SchedulingConfigRepository config;
    private final SchedulingFoundationService foundation;
    private final CommitmentRepository commitments;
    private final CommitmentDependencyRepository dependencies;
    private final ExecutionIdempotency idempotency;
    private final EventRepository events;
    private final JdbcTemplate db;
    private final ObjectMapper mapper = new ObjectMapper();
    public SchedulingPipeline(SchedulingConfigRepository config, SchedulingFoundationService foundation,
                              CommitmentRepository commitments, CommitmentDependencyRepository dependencies,
                              ExecutionIdempotency idempotency, EventRepository events, JdbcTemplate db) {
        this.config = config; this.foundation = foundation; this.commitments = commitments;
        this.dependencies = dependencies; this.idempotency = idempotency; this.events = events; this.db = db;
    }
    public record WorkInput(Long commitmentId, Integer workMinutes) {}
    public record Request(Instant startTime, Instant endTime, List<WorkInput> work, Long instructedCommitmentId) {}
    public record Placed(long id, SchedulingPlanner.Placement decision) {}
    public record Response(List<Placed> placements, List<Long> unplaced, List<SchedulingPlanner.Tie> importantTies) {}

    @Transactional
    public String generate(Long owner, String key, Request request) {
        var normalized = validate(request);
        config.lockOwner(owner);
        String fingerprint = "generate:" + mapper.writeValueAsString(normalized);
        String replay = idempotency.replay(owner, key, fingerprint);
        if (replay != null) return replay;
        // Lock commitments too: their metadata mutation path uses row locks rather than the owner lock.
        var owned = commitments.lockAllOwned(owner);
        var ids = owned.stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        var minutes = new TreeMap<Long, Integer>();
        for (var input : normalized.work()) {
            if (!ids.contains(input.commitmentId())) throw new ExecutionException(404, "Commitment not found");
            minutes.put(input.commitmentId(), input.workMinutes());
        }
        var eligibleGoals = new HashSet<>(db.query("SELECT id FROM goals WHERE user_id=? AND lifecycle_state='active' AND planning_state IN ('active','at_risk') FOR UPDATE",
                (r, n) -> r.getLong(1), owner));
        var risk = new HashSet<>(db.query("SELECT id FROM goals WHERE user_id=? AND planning_state='at_risk'", (r, n) -> r.getLong(1), owner));
        var ineligible = owned.stream().filter(c -> c.getGoalId() != null && !eligibleGoals.contains(c.getGoalId()))
                .map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        var scheduled = new HashSet<>(db.query("SELECT commitment_id FROM scheduled_blocks WHERE user_id=? AND commitment_id IS NOT NULL AND state IN ('scheduled','active')",
                (r, n) -> r.getLong(1), owner));
        scheduled.addAll(ineligible);
        var context = db.query("""
                SELECT b.id,b.start_time,b.end_time,COALESCE(c.category_id,ri.category_id) category_id FROM scheduled_blocks b
                LEFT JOIN commitments c ON c.id=b.commitment_id AND c.user_id=b.user_id
                LEFT JOIN recurring_intentions ri ON ri.id=b.recurring_intention_id AND ri.user_id=b.user_id
                WHERE b.user_id=? AND b.state IN ('scheduled','active','completed') AND b.start_time<?
                ORDER BY b.end_time,b.start_time,b.id
                """, (r, n) -> new SlotSelection.Context(r.getObject("start_time", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                r.getObject("end_time", LocalDateTime.class).toInstant(ZoneOffset.UTC), r.getObject("category_id", Long.class), r.getLong("id")), owner, utc(normalized.endTime()));
        var plan = new SchedulingPlanner().plan(owned, dependencies.findAllOwned(owner), risk, scheduled, minutes,
                normalized.instructedCommitmentId(), foundation.snapshot(owner, normalized.startTime(), normalized.endTime()), context);
        var placed = new ArrayList<Placed>();
        for (var placement : plan.placements()) {
            var generated = new GeneratedKeyHolder();
            db.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO scheduled_blocks(user_id,commitment_id,start_time,end_time,state,user_moved_flag,placement_reason)
                        VALUES(?,?,?,?,'scheduled',FALSE,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, owner); statement.setLong(2, placement.commitmentId());
                statement.setObject(3, utc(placement.startTime())); statement.setObject(4, utc(placement.endTime()));
                statement.setString(5, placement.placementReason()); return statement;
            }, generated);
            long id = Objects.requireNonNull(generated.getKey()).longValue();
            events.appendAndFlush(Event.forEntity("scheduled_block", id, "block.generated", "atlas",
                    placement.placementReason(), mapper.writeValueAsString(placement)));
            placed.add(new Placed(id, placement));
        }
        return idempotency.save(owner, key, fingerprint,
                mapper.writeValueAsString(new Response(List.copyOf(placed), plan.unplaced(), plan.importantTies())));
    }

    private Request validate(Request r) {
        if (r == null || r.startTime() == null || r.endTime() == null || !r.endTime().isAfter(r.startTime())
                || r.startTime().isBefore(Instant.parse("1001-01-01T00:00:00Z"))
                || r.endTime().isAfter(Instant.parse("9998-12-31T00:00:00Z"))
                || Duration.between(r.startTime(), r.endTime()).compareTo(Duration.ofDays(31)) > 0
                || r.startTime().getNano() % 1000 != 0 || r.endTime().getNano() % 1000 != 0
                || r.work() == null || r.work().size() > 1000)
            throw new IllegalArgumentException("Provide an explicit planning window of at most 31 days and at most 1000 work inputs");
        var ids = new HashSet<Long>();
        for (var input : r.work()) {
            if (input == null || input.commitmentId() == null || input.commitmentId() <= 0
                    || input.workMinutes() == null || input.workMinutes() < 1 || input.workMinutes() > 1440
                    || !ids.add(input.commitmentId()))
                throw new IllegalArgumentException("Provide distinct commitment IDs and 1–1440 work minutes each");
        }
        if (r.instructedCommitmentId() != null && !ids.contains(r.instructedCommitmentId()))
            throw new IllegalArgumentException("The instruction must target an item in work");
        return new Request(r.startTime(), r.endTime(), r.work().stream().sorted(Comparator.comparing(WorkInput::commitmentId)).toList(), r.instructedCommitmentId());
    }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i, ZoneOffset.UTC); }
}
