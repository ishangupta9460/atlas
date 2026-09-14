package com.atlas.backend.category;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryMigrationIntegrationTest {

    @Test
    void freshSchemaMigrationAppliesThroughV7() {
        Flyway flyway = flyway(dataSource("dom005_fresh"));

        assertEquals(7, flyway.migrate().migrationsExecuted);
    }

    @Test
    void upgradeFromV6ClearsLegacyUnresolvableCategoryReferencesBeforeAddingForeignKey() throws Exception {
        try (Connection connection = dataSource("dom005_upgrade").getConnection()) {
            DataSource dataSource = new SingleConnectionDataSource(connection, true);
            Flyway flyway = flyway(dataSource);
            assertEquals(6, flyway(dataSource, "6").migrate().migrationsExecuted);

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.update("INSERT INTO users (email, password_hash) VALUES (?, ?)",
                    "legacy-category@example.com", "legacy-password-hash");
            jdbcTemplate.update("""
                    INSERT INTO recurring_intentions
                        (user_id, title, target_count_per_week, current_week_remaining_count, category_id, flexibility_tier)
                    VALUES (1, 'Legacy category reference', 2, 2, 987654, 'flexible')
                    """);

            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recurring_intentions", Integer.class));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT category_id FROM recurring_intentions WHERE id = 1", Long.class));
            assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                    "UPDATE recurring_intentions SET category_id = ? WHERE id = 1", 987654));

            jdbcTemplate.update("""
                    INSERT INTO categories (user_id, name, default_flexibility_tier, color)
                    VALUES (1, 'Post-upgrade category', 'flexible', '#123456')
                    """);
            Long validCategoryId = jdbcTemplate.queryForObject(
                    "SELECT id FROM categories WHERE user_id = 1 AND name = 'Post-upgrade category'", Long.class);

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE recurring_intentions SET category_id = ? WHERE id = 1", validCategoryId));
            assertEquals(validCategoryId, jdbcTemplate.queryForObject(
                    "SELECT category_id FROM recurring_intentions WHERE id = 1", Long.class));
        }
    }

    private DataSource dataSource(String databaseName) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private Flyway flyway(DataSource dataSource) {
        // This historical test owns V6 -> V7, independently of later migrations.
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("7").load();
    }

    private Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }
}
