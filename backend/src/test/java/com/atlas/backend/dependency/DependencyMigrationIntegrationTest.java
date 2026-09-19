package com.atlas.backend.dependency;

import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DependencyMigrationIntegrationTest {
    DataSource source(String name) {
        return new DriverManagerDataSource(
            System.getProperty("dom007.mysql." + name + ".url", "jdbc:h2:mem:dom007_" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1"),
            System.getProperty("dom007.mysql.user", "sa"),
            System.getProperty("dom007.mysql.password", ""));
    }

    Flyway flyway(DataSource ds, String target) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load();
    }

    @Test void freshV10EnforcesUniqueSelfEdgeAndForeignKeys() throws Exception {
        try (var connection = source("fresh").getConnection()) {
            DataSource ds = new SingleConnectionDataSource(connection, true);
            var fw = flyway(ds, "10");
            assertEquals(10, fw.migrate().migrationsExecuted);
            fw.validate();
            assertEquals(0, fw.migrate().migrationsExecuted);
            var jdbc = new JdbcTemplate(ds);
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(1,'migration@example.com','test')");
            jdbc.update("INSERT INTO commitments(id,user_id,importance,flexibility_tier,work_state) VALUES(1,1,'high','flexible','draft'),(2,1,'high','flexible','draft')");
            jdbc.update("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(1,2)");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
            boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
            try (var stmt = connection.createStatement()) {
                var dup = assertThrows(java.sql.SQLException.class, () -> stmt.executeUpdate("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(1,2)"));
                assertEquals(mysql ? 1062 : 23505, dup.getErrorCode());
            }
            try (var stmt = connection.createStatement()) {
                var self = assertThrows(java.sql.SQLException.class, () -> stmt.executeUpdate("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(1,1)"));
                assertEquals(mysql ? 3819 : 23513, self.getErrorCode());
            }
            try (var stmt = connection.createStatement()) {
                var fk = assertThrows(java.sql.SQLException.class, () -> stmt.executeUpdate("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(1,999)"));
                assertEquals(mysql ? 1452 : 23506, fk.getErrorCode());
            }
        }
    }

    @Test void populatedV9UpgradePreservesCommitmentsAndEvents() throws Exception {
        try (var connection = source("upgrade").getConnection()) {
            DataSource ds = new SingleConnectionDataSource(connection, true);
            assertEquals(9, flyway(ds, "9").migrate().migrationsExecuted);
            var jdbc = new JdbcTemplate(ds);
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(1,'upgrade@example.com','test')");
            jdbc.update("INSERT INTO commitments(id,user_id,title,completion_criterion,importance,flexibility_tier,work_state) VALUES(1,1,'Task','Done','high','flexible','ready')");
            jdbc.update("INSERT INTO events(entity_type,entity_id,type,actor,payload) VALUES('commitment',1,'task.created','user','original')");
            var beforeCommitments = jdbc.queryForList("SELECT * FROM commitments ORDER BY id");
            var beforeEvents = jdbc.queryForList("SELECT * FROM events ORDER BY id");
            var fw = flyway(ds, "10");
            assertEquals(1, fw.migrate().migrationsExecuted);
            fw.validate();
            assertEquals(0, fw.migrate().migrationsExecuted);
            assertEquals(beforeCommitments, jdbc.queryForList("SELECT * FROM commitments ORDER BY id"));
            assertEquals(beforeEvents, jdbc.queryForList("SELECT * FROM events ORDER BY id"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
        }
    }
}
