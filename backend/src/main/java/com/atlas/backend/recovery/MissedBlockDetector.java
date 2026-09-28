package com.atlas.backend.recovery;

import com.atlas.backend.event.*;
import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.time.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unresolved means unknown, not failed. Execution and detection share the owner lock. */
@Service
public class MissedBlockDetector {
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final EventRepository events;
    private final ExecutionClock clock;
    public MissedBlockDetector(JdbcTemplate db, SchedulingConfigRepository config, EventRepository events, ExecutionClock clock) {
        this.db=db; this.config=config; this.events=events; this.clock=clock;
    }
    @Transactional
    public List<Long> detect(Long owner) {
        config.lockOwner(owner);
        var ids=db.query("""
            SELECT b.id FROM scheduled_blocks b WHERE b.user_id=? AND b.state='scheduled' AND b.end_time<=?
            AND NOT EXISTS (SELECT 1 FROM focus_sessions f WHERE f.scheduled_block_id=b.id)
            AND NOT EXISTS (SELECT 1 FROM actual_sessions a WHERE a.scheduled_block_id=b.id)
            ORDER BY b.end_time,b.id
            """,(r,n)->r.getLong(1),owner,LocalDateTime.ofInstant(clock.now(),ZoneOffset.UTC));
        for(long id:ids) {
            db.update("UPDATE scheduled_blocks SET state='unresolved' WHERE id=? AND user_id=?",id,owner);
            events.appendAndFlush(Event.forEntity("scheduled_block",id,"block.unresolved","atlas",
                "The planned window ended without a recorded session. What happened is not yet known."));
            var recurring=db.queryForObject("SELECT recurring_intention_id FROM scheduled_blocks WHERE id=?",Long.class,id);
            if(recurring!=null) events.appendAndFlush(Event.forEntity("recurring_intention",recurring,"recurring_intention.instance_missed","atlas",
                "No session was recorded in this window; the weekly target is unchanged and no debt is carried forward."));
        }
        return List.copyOf(ids);
    }
}
