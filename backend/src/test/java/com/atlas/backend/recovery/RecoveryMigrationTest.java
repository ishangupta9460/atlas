package com.atlas.backend.recovery;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryMigrationTest {
    @Test void freshAndRepeatedStartup() {
        var source=new SingleConnectionDataSource("jdbc:h2:mem:recovery_fresh;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","",true);
        // Historical Chunk 4 boundary; Chunk 5 separately verifies V16 -> V18 and a fresh V18 database.
        var flyway=Flyway.configure().dataSource(source).target("16").load();assertEquals(16,flyway.migrate().migrationsExecuted);
        flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
    }
    @Test void populatedUpgradePreservesLegacyAndExecutionHistory() {
        var source=new SingleConnectionDataSource("jdbc:h2:mem:recovery_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","",true);
        Flyway.configure().dataSource(source).target("13").load().migrate();var db=new JdbcTemplate(source);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'upgrade@test.example','unused')");
        db.update("INSERT INTO tasks(user_id,title,status) VALUES(1,'Legacy','ready')");
        db.update("INSERT INTO recurring_intentions(id,user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(1,1,'Practice',3,1,'flexible')");
        db.update("INSERT INTO scheduled_blocks(id,user_id,recurring_intention_id,start_time,end_time,state,placement_reason) VALUES(1,1,1,'2026-09-21 12:00:00','2026-09-21 13:00:00','completed','Original')");
        db.update("INSERT INTO actual_sessions(scheduled_block_id,actual_start,actual_end,active_millis,user_reported_outcome,completion_pct) VALUES(1,'2026-09-21 12:00:00','2026-09-21 13:00:00',1800000,'Original report',40)");
        var history=db.queryForList("SELECT * FROM actual_sessions");var flyway=Flyway.configure().dataSource(source).target("16").load();
        assertEquals(3,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(history,db.queryForList("SELECT * FROM actual_sessions"));assertEquals("Legacy",db.queryForObject("SELECT title FROM tasks",String.class));
        assertEquals(1,db.queryForObject("SELECT current_week_remaining_count FROM recurring_intentions",Integer.class));
    }
}
