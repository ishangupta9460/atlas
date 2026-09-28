package com.atlas.backend.execution;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionMigrationIntegrationTest {
    @Test void freshDatabaseValidatesAllMigrations() {
        var source=new SingleConnectionDataSource("jdbc:h2:mem:execution_fresh;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","",true);
        var flyway=Flyway.configure().dataSource(source).target("13").load();
        assertEquals(13,flyway.migrate().migrationsExecuted); flyway.validate();
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }

    @Test void populatedUpgradePreservesFinishedHistoryAndDefaultsMarkerToNull() {
        var source=new SingleConnectionDataSource("jdbc:h2:mem:execution_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","",true);
        Flyway.configure().dataSource(source).target("12").load().migrate();
        var db=new JdbcTemplate(source);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'upgrade@example.test','unused')");
        db.update("INSERT INTO tasks(user_id,title,status) VALUES(1,'Legacy','ready')");
        db.update("INSERT INTO recurring_intentions(id,user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(1,1,'Work',1,1,'flexible')");
        db.update("INSERT INTO scheduled_blocks(id,user_id,recurring_intention_id,start_time,end_time,state,placement_reason) VALUES(1,1,1,'2026-09-25 12:00:00','2026-09-25 13:00:00','completed','Existing')");
        db.update("INSERT INTO focus_sessions(scheduled_block_id,state,actual_start,active_millis) VALUES(1,'finished','2026-09-25 12:00:00',1800000)");
        db.update("INSERT INTO actual_sessions(scheduled_block_id,actual_start,actual_end,active_millis,user_reported_outcome,completion_pct) VALUES(1,'2026-09-25 12:00:00','2026-09-25 13:00:00',1800000,'Original report',40)");
        var facts=db.queryForList("SELECT * FROM actual_sessions");
        var flyway=Flyway.configure().dataSource(source).target("13").load();
        assertEquals(1,flyway.migrate().migrationsExecuted); flyway.validate();
        assertEquals(facts,db.queryForList("SELECT * FROM actual_sessions"));
        assertNull(db.queryForObject("SELECT overrun_prompted_at FROM focus_sessions",java.time.LocalDateTime.class));
        assertEquals("Legacy",db.queryForObject("SELECT title FROM tasks",String.class));
    }
}
