package com.atlas.backend.fixedcommitment;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class FixedCommitmentMigrationIntegrationTest {
    // Optional URLs must identify empty, isolated verification databases; never the deployed database.
    private DataSource dataSource(String scenario) {
        return new DriverManagerDataSource(System.getProperty("dom006.mysql." + scenario + ".url",
                "jdbc:h2:mem:dom006_" + scenario + ";MODE=MySQL;DB_CLOSE_DELAY=-1"),
                System.getProperty("dom006.mysql.user", "sa"), System.getProperty("dom006.mysql.password", ""));
    }
    private Flyway flyway(DataSource source, String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target).load();
    }

    @Test void freshDatabaseAppliesThroughV8AndEnforcesConstraints() throws Exception {
        // Keep the migration connection alive: H2 IN checks otherwise retain a closed session.
        try (var connection = dataSource("fresh").getConnection()) {
        DataSource source = new SingleConnectionDataSource(connection, true);
        Flyway flyway = flyway(source, "8");
        assertEquals(8, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (1, 'fixed-schema@example.com', 'not-used')");
        String insert = "INSERT INTO fixed_commitments (user_id, title, start_time, end_time, source, recurrence_rule) VALUES (?, ?, ?, ?, ?, ?)";
        for (String provenance : new String[]{"manual", "screenshot_import"}) {
            assertEquals(1, jdbc.update(insert, 1, "Reservation", "2026-09-14 10:00:00.123456", "2026-09-14 11:00:00", provenance, null));
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fixed_commitments", Integer.class));
        assertEquals(123456000, jdbc.queryForObject("SELECT start_time FROM fixed_commitments WHERE source = 'manual'", java.sql.Timestamp.class).getNanos());
        Object[][] invalid = {
                {999, "Valid", "2026-09-14 10:00:00", "2026-09-14 11:00:00", "manual", null},
                {null, "Valid", "2026-09-14 10:00:00", "2026-09-14 11:00:00", "manual", null},
                {1, null, "2026-09-14 10:00:00", "2026-09-14 11:00:00", "manual", null},
                {1, "Valid", null, "2026-09-14 11:00:00", "manual", null},
                {1, "Valid", "2026-09-14 10:00:00", null, "manual", null},
                {1, "Valid", "2026-09-14 10:00:00", "2026-09-14 11:00:00", null, null},
                {1, "Valid", "2026-09-14 10:00:00", "2026-09-14 11:00:00", "forged", null},
                {1, "Valid", "2026-09-14 10:00:00", "2026-09-14 10:00:00", "manual", null},
                {1, "Valid", "2026-09-14 10:00:00", "2026-09-14 09:00:00", "manual", null}
        };
        boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        for (int index = 0; index < invalid.length; index++) {
            try (var statement = connection.prepareStatement(insert)) {
                Object[] values = invalid[index];
                for (int parameter = 0; parameter < values.length; parameter++) statement.setObject(parameter + 1, values[parameter]);
                var failure = assertThrows(java.sql.SQLException.class, statement::executeUpdate);
                // Assert the actual FK / NOT NULL / CHECK rejection, not just any JDBC failure.
                int expected = index == 0 ? (mysql ? 1452 : 23506)
                        : index <= 5 ? (mysql ? 1048 : 23502) : (mysql ? 3819 : 23513);
                assertEquals(expected, failure.getErrorCode(), "Constraint case " + index);
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fixed_commitments", Integer.class));
            }
        }
        // Schema stores opaque future recurrence without defining a grammar or expanding it.
        assertEquals(1, jdbc.update(insert, 1, "Future import", "2026-09-14 10:00:00", "2026-09-14 11:00:00", "screenshot_import", "opaque"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM users WHERE id = 1"));
        }
    }

    @Test void populatedV7UpgradePreservesExistingRowsLinksAndEvents() throws Exception {
        try (var connection = dataSource("upgrade").getConnection()) {
        DataSource source = new SingleConnectionDataSource(connection, true);
        assertEquals(7, flyway(source, "7").migrate().migrationsExecuted);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (1, 'upgrade@example.com', 'not-used')");
        jdbc.update("INSERT INTO categories (id, user_id, name, default_flexibility_tier, color) VALUES (1, 1, 'Study', 'fixed', 'blue')");
        jdbc.update("INSERT INTO recurring_intentions (id, user_id, title, target_count_per_week, current_week_remaining_count, category_id, flexibility_tier) VALUES (1, 1, 'Practice', 3, 2, 1, 'fixed')");
        jdbc.update("INSERT INTO tasks (id, user_id, title, status) VALUES (1, 1, 'Existing', 'ready')");
        jdbc.update("INSERT INTO events (task_id, type, entity_type, entity_id, actor, payload) VALUES (1, 'task.created', 'task', 1, 'user', 'original')");
        Flyway flyway = flyway(source, "8");
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(1L, jdbc.queryForObject("SELECT category_id FROM recurring_intentions WHERE id = 1", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT current_week_remaining_count FROM recurring_intentions WHERE id = 1", Integer.class));
        assertEquals("Study", jdbc.queryForObject("SELECT name FROM categories WHERE id = 1", String.class));
        assertEquals("ready", jdbc.queryForObject("SELECT status FROM tasks WHERE id = 1", String.class));
        assertEquals("original", jdbc.queryForObject("SELECT payload FROM events WHERE task_id = 1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fixed_commitments", Integer.class));
        assertEquals(1, jdbc.update("INSERT INTO fixed_commitments (user_id, title, start_time, end_time, source) VALUES (1, 'After upgrade', '2026-09-14 10:00:00', '2026-09-14 11:00:00', 'manual')"));
        }
    }
}
