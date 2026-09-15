package com.atlas.backend.commitment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class CommitmentMigrationIntegrationTest {
    DataSource source(String name) { return new DriverManagerDataSource(System.getProperty("dom003.mysql."+name+".url","jdbc:h2:mem:dom003_"+name+";MODE=MySQL;DB_CLOSE_DELAY=-1"),System.getProperty("dom003.mysql.user","sa"),System.getProperty("dom003.mysql.password","")); }
    Flyway flyway(DataSource ds,String target) { return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load(); }
    @Test void freshV9EnforcesEnumsRelationshipsAndProgress() throws Exception {
        try(var connection=source("fresh").getConnection()) {
            DataSource ds=new SingleConnectionDataSource(connection,true); var fw=flyway(ds,"9");
            assertEquals(9,fw.migrate().migrationsExecuted); fw.validate(); assertEquals(0,fw.migrate().migrationsExecuted);
            var jdbc=new JdbcTemplate(ds);
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(1,'migration@example.com','test')");
            for(String state:Commitment.STATES) jdbc.update("INSERT INTO commitments(user_id,title,completion_criterion,importance,flexibility_tier,work_state) VALUES(1,'Task','Done','high','flexible',?)",state);
            assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM commitments",Integer.class));
            boolean mysql=connection.getMetaData().getDatabaseProductName().equals("MySQL");
            String insert="INSERT INTO commitments(user_id,importance,flexibility_tier,work_state,current_completion_pct) VALUES(?,?,?,?,?)";
            Object[][] invalid={{999,"high","flexible","draft",0},{null,"high","flexible","draft",0},{1,"urgent","flexible","draft",0},{1,"high","floating","draft",0},{1,"high","flexible","paused",0},{1,"high","flexible","draft",-1},{1,"high","flexible","draft",101},{1,"high","flexible","ready",0}};
            for(int i=0;i<invalid.length;i++) {
                try(var stmt=connection.prepareStatement(insert)) {
                    for(int j=0;j<5;j++)stmt.setObject(j+1,invalid[i][j]);
                    var ex=assertThrows(java.sql.SQLException.class,stmt::executeUpdate);
                    assertEquals(i==0?(mysql?1452:23506):i==1?(mysql?1048:23502):(mysql?3819:23513),ex.getErrorCode());
                }
            }
            assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM commitments",Integer.class));
            jdbc.update("UPDATE commitments SET own_deadline='2026-09-20 13:00:00.123456' WHERE id=1");
            assertEquals(123456000,jdbc.queryForObject("SELECT own_deadline FROM commitments WHERE id=1",java.sql.Timestamp.class).getNanos());
            jdbc.update("INSERT INTO goals(id,user_id,title,lifecycle_state,planning_state) VALUES(1,1,'Goal','active','active')");
            jdbc.update("UPDATE commitments SET goal_id=1 WHERE id=1");
            for(String fk:new String[]{"goal_id","milestone_id","category_id"}) {
                try(var stmt=connection.createStatement()) {
                    var failure=assertThrows(java.sql.SQLException.class,()->stmt.executeUpdate("UPDATE commitments SET "+fk+"=999 WHERE id=1"));
                    assertEquals(mysql?1452:23506,failure.getErrorCode());
                }
            }
            jdbc.update("INSERT INTO categories(user_id,name,default_flexibility_tier,color) VALUES(1,'Study','protected','blue')");
            for(String value:Commitment.IMPORTANCE) jdbc.update("UPDATE categories SET default_importance=?",value);
            try(var stmt=connection.createStatement()) {
                var failure=assertThrows(java.sql.SQLException.class,()->stmt.executeUpdate("UPDATE categories SET default_importance='urgent'"));
                assertEquals(mysql?3819:23513,failure.getErrorCode());
            }
        }
    }
    @Test void populatedV8UpgradePreservesLegacyRowsEventsAndCategories() throws Exception {
        try(var connection=source("upgrade").getConnection()) {
            DataSource ds=new SingleConnectionDataSource(connection,true); assertEquals(8,flyway(ds,"8").migrate().migrationsExecuted);
            var jdbc=new JdbcTemplate(ds);
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(1,'upgrade@example.com','test')");
            jdbc.update("INSERT INTO categories(id,user_id,name,default_flexibility_tier,color) VALUES(1,1,'Study','protected','blue')");
            for(String state:new String[]{"ready","in_progress","completed"}) jdbc.update("INSERT INTO tasks(user_id,title,status,started_at,finished_at) VALUES(1,'Legacy',?,'2026-09-14 10:00:00.123456','2026-09-14 11:00:00.123456')",state);
            jdbc.update("INSERT INTO events(task_id,entity_type,entity_id,type,actor,payload) VALUES(1,'task',1,'task.finished','user','original')");
            var before=jdbc.queryForList("SELECT * FROM tasks ORDER BY id"); var history=jdbc.queryForList("SELECT * FROM events ORDER BY id");
            var fw=flyway(ds,"9"); assertEquals(1,fw.migrate().migrationsExecuted); fw.validate(); assertEquals(0,fw.migrate().migrationsExecuted);
            assertEquals(before,jdbc.queryForList("SELECT * FROM tasks ORDER BY id")); assertEquals(history,jdbc.queryForList("SELECT * FROM events ORDER BY id"));
            assertNull(jdbc.queryForObject("SELECT default_importance FROM categories WHERE id=1",String.class));
            assertEquals("protected",jdbc.queryForObject("SELECT default_flexibility_tier FROM categories WHERE id=1",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM commitments",Integer.class));
        }
    }
}
