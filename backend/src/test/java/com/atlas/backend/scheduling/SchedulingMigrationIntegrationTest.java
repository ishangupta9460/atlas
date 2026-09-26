package com.atlas.backend.scheduling;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingMigrationIntegrationTest {
    private DataSource source(String scenario) {
        // Optional MySQL URLs must point to empty, isolated verification databases.
        return new DriverManagerDataSource(System.getProperty("scheduling.mysql." + scenario + ".url",
                "jdbc:h2:mem:scheduling_" + scenario + ";MODE=MySQL;DB_CLOSE_DELAY=-1"),
                System.getProperty("scheduling.mysql.user", "sa"), System.getProperty("scheduling.mysql.password", ""));
    }

    @Test void freshSchemaAppliesValidatesAndEnforcesConstraints() throws Exception {
        try (var connection = source("fresh").getConnection()) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            var flyway = Flyway.configure().dataSource(dataSource).target("12").load();
            assertEquals(12, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var db = new JdbcTemplate(dataSource);
            db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'scheduling-migration@test.example','unused')");
            db.update("INSERT INTO scheduling_config(user_id) VALUES(1)");
            db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(1,1,'09:00:00','17:00:00','working')");
            for (String invalid : new String[]{
                    "INSERT INTO scheduling_config(user_id) VALUES(999)",
                    "UPDATE scheduling_config SET workable_fraction=1.1 WHERE user_id=1",
                    "UPDATE scheduling_config SET break_minutes=0 WHERE user_id=1",
                    "UPDATE working_hours_config SET day_of_week=8",
                    "UPDATE working_hours_config SET kind='unknown'",
                    "UPDATE working_hours_config SET end_time=start_time"}) {
                try (var statement = connection.createStatement()) {
                    var failure = assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(invalid));
                    assertTrue(java.util.Set.of(23506, 23513, 1452, 3819).contains(failure.getErrorCode()), failure::getMessage);
                }
            }
        }
    }

    @Test void populatedV11UpgradePreservesExistingExecutionAndFixedRows() throws Exception {
        try (var connection = source("upgrade").getConnection()) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(dataSource).target("11").load().migrate();
            var db = new JdbcTemplate(dataSource);
            db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'scheduling-upgrade@test.example','unused')");
            db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(1,'Fixed','2026-09-25 10:00:00','2026-09-25 11:00:00','manual')");
            db.update("INSERT INTO recurring_intentions(id,user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(1,1,'Work',1,1,'flexible')");
            db.update("INSERT INTO scheduled_blocks(user_id,recurring_intention_id,start_time,end_time,placement_reason) VALUES(1,1,'2026-09-25 12:00:00','2026-09-25 13:00:00','Existing')");
            var flyway = Flyway.configure().dataSource(dataSource).target("12").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM fixed_commitments", Integer.class));
            assertEquals("Existing", db.queryForObject("SELECT placement_reason FROM scheduled_blocks", String.class));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM scheduling_config", Integer.class));
        }
    }
}
