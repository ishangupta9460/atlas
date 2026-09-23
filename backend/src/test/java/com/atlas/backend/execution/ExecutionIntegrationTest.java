package com.atlas.backend.execution;

import com.atlas.backend.commitment.*;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:core_execution;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class ExecutionIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired CommitmentService commitments;
    @Autowired ExecutionService service;
    @Autowired JdbcTemplate db;
    final ObjectMapper mapper=new ObjectMapper();
    MockMvc mvc;
    Long owner;
    String token, foreign;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user=users.save(User.of(UUID.randomUUID()+"@test.example","test")); owner=user.getId(); token=jwt.generateToken(user);
        foreign=jwt.generateToken(users.save(User.of(UUID.randomUUID()+"@test.example","test")));
    }
    @AfterEach void clean() {
        db.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS reject_execution_event");
        db.update("UPDATE scheduled_blocks SET superseded_by_block_id=NULL");
        for(String table:new String[]{"execution_idempotency","actual_sessions","focus_sessions","scheduled_blocks","events","commitment_dependency","commitments","fixed_commitments","tasks","milestones","roadmaps","goals","categories","users"}) db.update("DELETE FROM "+table);
    }
    long task() {
        return commitments.create(owner,mapper.readValue("{\"title\":\"Build flow\",\"completionCriterion\":\"Flow works\",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}",CommitmentRequest.class)).id();
    }
    ExecutionController.Placement placement(long task) { return new ExecutionController.Placement(task,Instant.now().minusSeconds(1),Instant.now().plusSeconds(1500)); }
    long block(long task) { return mapper.readTree(service.place(owner,UUID.randomUUID().toString(),placement(task))).path("id").asLong(); }
    ResultActions action(long id,String action,String key,String body) throws Exception {
        return mvc.perform(post("/blocks/"+id+"/session/"+action).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body));
    }
    String report(int pct) { return "{\"report\":\"Implemented the first part\",\"completionPct\":"+pct+"}"; }
    @Test void fullLoopPersistsPauseAndImmutableHistoryAndReplays() throws Exception {
        long task=task(),block=block(task);
        String first=action(block,"start","start","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("running")).andReturn().getResponse().getContentAsString();
        assertEquals(first,action(block,"start","start","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        action(block,"pause","pause","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("paused"));
        var paused=service.workspace(owner).blocks().get(0);
        assertNull(paused.runningSince());
        action(block,"resume","resume","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("running"));
        action(block,"finish","finish",report(40)).andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("finished"));
        assertEquals("ready",commitments.get(owner,task).workState());
        assertEquals(new BigDecimal("40.00"),commitments.get(owner,task).currentCompletionPct());
        var firstHistory=service.workspace(owner).history().get(0);
        long next=block(task);
        action(next,"start","second-start","{}").andExpect(status().isOk());
        action(next,"finish","second-finish",report(100)).andExpect(status().isOk());
        assertEquals("completed",commitments.get(owner,task).workState());
        assertTrue(service.workspace(owner).history().contains(firstHistory));
        action(block,"finish","finish",report(40)).andExpect(status().isOk());
        assertEquals(2,service.workspace(owner).history().size());
        assertEquals("completed",commitments.get(owner,task).workState());
    }
    @Test void validatesOwnershipIdempotencyTransitionsAndPrecision() throws Exception {
        long block=block(task());
        mvc.perform(post("/blocks/"+block+"/session/start").header("Authorization","Bearer "+foreign).header("Idempotency-Key","foreign")).andExpect(status().isNotFound());
        mvc.perform(get("/execution").header("Authorization","Bearer "+foreign)).andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty()).andExpect(jsonPath("$.blocks").isEmpty());
        mvc.perform(get("/execution")).andExpect(status().isUnauthorized());
        mvc.perform(post("/blocks/"+block+"/session/start").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        action(block,"pause","wrong","{}").andExpect(status().isConflict());
        action(block,"start","same","{}").andExpect(status().isOk());
        action(block,"pause","same","{}").andExpect(status().isConflict());
        action(block,"finish","precision","{\"report\":\"Part\",\"completionPct\":99.999}").andExpect(status().isBadRequest());
        action(block,"finish","blank","{\"report\":\" \",\"completionPct\":100}").andExpect(status().isBadRequest());
        assertEquals(0,service.workspace(owner).history().size());
    }
    @Test void rejectsOverlapsDependenciesAndNewFixedConflictAtStart() throws Exception {
        long t=task(),b=block(t),other=task();
        assertThrows(ExecutionException.class,()->block(other));
        db.update("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(?,?)",other,t);
        action(b,"start","blocked","{}").andExpect(status().isConflict());
        db.update("DELETE FROM commitment_dependency");
        mvc.perform(post("/fixed-commitments").header("Authorization","Bearer "+token).contentType("application/json").content("{\"title\":\"Meeting\",\"startTime\":\""+Instant.now()+"\",\"endTime\":\""+Instant.now().plusSeconds(600)+"\"}")).andExpect(status().isCreated());
        action(b,"start","fixed","{}").andExpect(status().isConflict());
        assertEquals(1,service.workspace(owner).fixed().size());
    }
    @Test void moveKeepsOldBlockAndReplaysWithoutDuplicate() {
        long t=task(),b=block(t);
        var moved=new ExecutionController.Placement(t,Instant.now().plusSeconds(3600),Instant.now().plusSeconds(5100));
        String first=service.move(owner,b,"move",moved);
        assertEquals(first,service.move(owner,b,"move",moved));
        assertEquals(2,service.workspace(owner).blocks().size());
        assertEquals("superseded",service.workspace(owner).blocks().get(0).state());
        assertNotNull(db.queryForObject("SELECT superseded_by_block_id FROM scheduled_blocks WHERE id=?",Long.class,b));
    }
    @Test void finishEventFailureRollsBackEveryWrite() {
        long t=task(),b=block(t); service.transition(owner,b,"start","start",null);
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_execution_event CHECK(type <> 'session.finished')");
        assertThrows(RuntimeException.class,()->service.transition(owner,b,"finish","finish",new ExecutionController.Report("Done",new BigDecimal("100"))));
        assertEquals("in_progress",commitments.get(owner,t).workState());
        assertEquals(new BigDecimal("0.00"),commitments.get(owner,t).currentCompletionPct());
        assertEquals("running",service.workspace(owner).blocks().get(0).sessionState());
        assertEquals(0,service.workspace(owner).history().size());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency WHERE request_key='finish'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM events WHERE type IN ('task.completed','task.progress_changed','block.completed')",Integer.class));
    }
    @Test void placementEventFailureRollsBackBlockAndReplayKey() {
        long t=task();
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_execution_event CHECK(type <> 'block.placed')");
        assertThrows(RuntimeException.class,()->block(t));
        assertTrue(service.workspace(owner).blocks().isEmpty());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency",Integer.class));
    }
    @Test void secondActiveSessionRejectedEvenWhenPaused() {
        long first=block(task()); service.transition(owner,first,"start","start",null); service.transition(owner,first,"pause","pause",null);
        // A separate non-overlapping planned window can arrive while the first session stays paused.
        db.update("UPDATE scheduled_blocks SET start_time=DATEADD('HOUR',-2,start_time),end_time=DATEADD('HOUR',-2,end_time) WHERE id=?",first);
        long second=block(task());
        assertThrows(ExecutionException.class,()->service.transition(owner,second,"start","second",null));
        assertEquals("paused",service.workspace(owner).blocks().get(0).sessionState());
    }
}
