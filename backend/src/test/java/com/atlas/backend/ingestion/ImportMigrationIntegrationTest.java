package com.atlas.backend.ingestion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ImportMigrationIntegrationTest {
    @Test void freshAndPopulatedUpgradeAreRepeatableAndEnforceOwnership() throws Exception {
        for(String scenario:new String[]{"fresh","upgrade"})try(var connection=new DriverManagerDataSource(System.getProperty("chunk5.mysql."+scenario+".url","jdbc:h2:mem:chunk5_"+scenario+";MODE=MySQL;DB_CLOSE_DELAY=-1"),System.getProperty("chunk5.mysql.user","sa"),System.getProperty("chunk5.mysql.password","")).getConnection()){
            var source=new SingleConnectionDataSource(connection,true);var db=new JdbcTemplate(source);
            var base=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("16").load();
            if(scenario.equals("upgrade")){
                base.migrate();db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'one@example.com','test')");
                db.update("INSERT INTO commitments(id,user_id,title,importance,flexibility_tier,work_state) VALUES(1,1,'Preserve me','medium','flexible','draft')");
                db.update("INSERT INTO events(type,entity_type,entity_id,actor,payload) VALUES('task.created','commitment',1,'user','original')");
            }
            var all=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("18").load();
            assertEquals(scenario.equals("upgrade")?2:18,all.migrate().migrationsExecuted);all.validate();assertEquals(0,all.migrate().migrationsExecuted);
            if(scenario.equals("fresh")){db.update("INSERT INTO users(id,email,password_hash) VALUES(1,'one@example.com','test')");db.update("INSERT INTO commitments(id,user_id,title,importance,flexibility_tier,work_state) VALUES(1,1,'Preserve me','medium','flexible','draft')");}
            else assertEquals("original",db.queryForObject("SELECT payload FROM events WHERE entity_id=1",String.class));
            assertEquals("Preserve me",db.queryForObject("SELECT title FROM commitments WHERE id=1",String.class));
            db.update("INSERT INTO users(id,email,password_hash) VALUES(2,'two@example.com','test')");
            db.update("INSERT INTO resources(id,user_id,type,title,url_or_file_ref) VALUES(1,1,'link','Guide','https://example.com')");
            db.update("INSERT INTO task_resource(user_id,commitment_id,resource_id) VALUES(1,1,1)");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO task_resource(user_id,commitment_id,resource_id) VALUES(2,1,1)"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO resource_feedback(user_id,resource_id,reaction,created_at) VALUES(2,1,'liked',CURRENT_TIMESTAMP)"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO resources(user_id,type,title,url_or_file_ref) VALUES(1,'unknown','Bad','ref')"));
            db.update("INSERT INTO import_proposals(user_id,kind,filename,media_type,upload_bytes,proposal_json) VALUES(1,'roadmap','plan.md','text/plain',?,'{}')",new byte[]{65});
            assertEquals("review",db.queryForObject("SELECT state FROM import_proposals",String.class));
            String large="x".repeat(100000);
            db.update("INSERT INTO execution_idempotency(user_id,request_key,fingerprint,response_json) VALUES(1,'large',?,?)",large,large);
            assertEquals(large,db.queryForObject("SELECT response_json FROM execution_idempotency WHERE request_key='large'",String.class));
            try(var indexes=connection.getMetaData().getIndexInfo(null,null,connection.getMetaData().getDatabaseProductName().equals("MySQL")?"resources":"RESOURCES",false,false)){
                boolean found=false;while(indexes.next())if("idx_resources_owner".equalsIgnoreCase(indexes.getString("INDEX_NAME")))found=true;assertTrue(found);
            }
        }
    }
}
