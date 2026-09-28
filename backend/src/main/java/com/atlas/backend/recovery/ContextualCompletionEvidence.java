package com.atlas.backend.recovery;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded historical read seam; later analytics can supply the same contextual facts. */
@Component
public class ContextualCompletionEvidence {
    public record Fact(long blockId, Long categoryId, Long recurringIntentionId, Instant start, boolean completed) {}
    private final JdbcTemplate db;
    private final int days;
    public ContextualCompletionEvidence(JdbcTemplate db,@Value("${atlas.recovery.history-days:84}") int days) {
        if(days<1) throw new IllegalArgumentException("History window must be positive");
        this.db=db; this.days=days;
    }
    public List<Fact> read(Long owner,Instant now) {
        return db.query("""
            SELECT b.id,COALESCE(c.category_id,ri.category_id),b.recurring_intention_id,b.start_time,a.completion_pct
            FROM scheduled_blocks b LEFT JOIN commitments c ON c.id=b.commitment_id
            LEFT JOIN recurring_intentions ri ON ri.id=b.recurring_intention_id
            LEFT JOIN actual_sessions a ON a.scheduled_block_id=b.id
            WHERE b.user_id=? AND b.end_time<=? AND b.start_time>=? AND b.state<>'cancelled'
            AND (b.state<>'superseded' OR a.id IS NOT NULL OR EXISTS
                (SELECT 1 FROM events e WHERE e.entity_type='scheduled_block' AND e.entity_id=b.id AND e.type IN ('block.unresolved','block.reported')))
            ORDER BY b.start_time,b.id
            """,(r,n)->new Fact(r.getLong(1),r.getObject(2,Long.class),r.getObject(3,Long.class),
                r.getObject(4,LocalDateTime.class).toInstant(ZoneOffset.UTC),
                r.getBigDecimal(5)!=null && r.getBigDecimal(5).compareTo(BigDecimal.valueOf(100))==0),
            owner,LocalDateTime.ofInstant(now,ZoneOffset.UTC),LocalDateTime.ofInstant(now.minus(Duration.ofDays(days)),ZoneOffset.UTC));
    }
}
