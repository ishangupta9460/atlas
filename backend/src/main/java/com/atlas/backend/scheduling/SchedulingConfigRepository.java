package com.atlas.backend.scheduling;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC matches V11 execution persistence; every query is bound to the authenticated owner. */
@Repository
public class SchedulingConfigRepository {
    private final JdbcTemplate db;
    public SchedulingConfigRepository(JdbcTemplate db) { this.db = db; }

    public void lockOwner(Long owner) {
        db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner);
    }

    public Optional<CapacityCalculator.Policy> capacity(Long owner) {
        return db.query("SELECT * FROM scheduling_config WHERE user_id=?", (r, n) ->
                new CapacityCalculator.Policy(r.getBigDecimal("workable_fraction"), r.getInt("buffer_minutes"),
                        r.getInt("continuous_work_minutes"), r.getInt("break_minutes")), owner).stream().findFirst();
    }

    public void ensureConfig(Long owner) {
        // Caller holds the users row lock, including for first configuration creation.
        if (capacity(owner).isEmpty()) db.update("INSERT INTO scheduling_config(user_id) VALUES(?)", owner);
    }

    public void saveCapacity(Long owner, CapacityCalculator.Policy policy) {
        db.update("""
            UPDATE scheduling_config SET workable_fraction=?, buffer_minutes=?, continuous_work_minutes=?,
                break_minutes=? WHERE user_id=?
            """, policy.workableFraction(), policy.bufferMinutes(), policy.continuousWorkMinutes(),
                policy.breakMinutes(), owner);
    }

    public Optional<WorkingHours> workingHours(Long owner) {
        var zones = db.query("SELECT timezone FROM scheduling_config WHERE user_id=? AND timezone IS NOT NULL",
                (r, n) -> r.getString(1), owner);
        if (zones.isEmpty()) return Optional.empty();
        var windows = db.query("SELECT * FROM working_hours_config WHERE user_id=? ORDER BY day_of_week,start_time,end_time,kind",
                (r, n) -> new WorkingHours.Window(r.getInt("day_of_week"),
                        r.getObject("start_time", java.time.LocalTime.class), r.getObject("end_time", java.time.LocalTime.class),
                        r.getString("kind")), owner);
        return Optional.of(new WorkingHours(zones.get(0), windows));
    }

    public void saveWorkingHours(Long owner, WorkingHours hours) {
        db.update("UPDATE scheduling_config SET timezone=? WHERE user_id=?", hours.timezone(), owner);
        db.update("DELETE FROM working_hours_config WHERE user_id=?", owner);
        db.batchUpdate("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,?,?,?)",
                hours.windows(), 224, (statement, w) -> {
                    statement.setLong(1, owner); statement.setInt(2, w.dayOfWeek());
                    statement.setObject(3, w.startTime()); statement.setObject(4, w.endTime()); statement.setString(5, w.kind());
                });
    }

    public List<TimeInterval> fixed(Long owner, TimeInterval bounds) {
        return db.query("SELECT start_time,end_time FROM fixed_commitments WHERE user_id=? AND start_time<? AND end_time>? ORDER BY start_time,end_time,id",
                (r, n) -> new TimeInterval(r.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        r.getObject(2, LocalDateTime.class).toInstant(ZoneOffset.UTC)), owner,
                utc(bounds.end()), utc(bounds.start()));
    }

    public List<TimeInterval> blocks(Long owner, TimeInterval bounds) {
        // Superseded history must not occupy time. Completed blocks still consumed that day's capacity.
        return db.query("""
            SELECT start_time,end_time FROM scheduled_blocks
            WHERE user_id=? AND state IN ('scheduled','active','completed') AND start_time<? AND end_time>?
            ORDER BY start_time,end_time,id
            """, (r, n) -> new TimeInterval(r.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC),
                        r.getObject(2, LocalDateTime.class).toInstant(ZoneOffset.UTC)), owner,
                utc(bounds.end()), utc(bounds.start()));
    }

    private static LocalDateTime utc(java.time.Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
