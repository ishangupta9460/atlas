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
    @org.springframework.test.context.bean.override.mockito.MockitoBean ExecutionClock clock;
    final ObjectMapper mapper=new ObjectMapper();
    MockMvc mvc;
    Long owner;
    String token, foreign;
    @BeforeEach void setup() {
        org.mockito.Mockito.when(clock.now()).thenReturn(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user=users.save(User.of(UUID.randomUUID()+"@test.example","test")); owner=user.getId(); token=jwt.generateToken(user);
        db.update("INSERT INTO scheduling_config(user_id,timezone,workable_fraction,buffer_minutes) VALUES(?,'UTC',1,0)",owner);
        for(int day=1;day<=7;day++) {
            db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'00:00:00','12:00:00','working')",owner,day);
            db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'12:00:00','00:00:00','working')",owner,day);
        }
        foreign=jwt.generateToken(users.save(User.of(UUID.randomUUID()+"@test.example","test")));
    }
    @AfterEach void clean() {
        db.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS reject_execution_event");
        db.update("UPDATE scheduled_blocks SET superseded_by_block_id=NULL");
        for(String table:new String[]{"working_hours_config","scheduling_config","execution_idempotency","actual_sessions","focus_sessions","scheduled_blocks","events","commitment_dependency","commitments","fixed_commitments","tasks","milestones","roadmaps","goals","categories","users"}) db.update("DELETE FROM "+table);
    }
    long task() {
        return commitments.create(owner,mapper.readValue("{\"title\":\"Build flow\",\"completionCriterion\":\"Flow works\",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}",CommitmentRequest.class)).id();
    }
    ExecutionController.Placement placement(long task) { return new ExecutionController.Placement(task,clock.now().minusSeconds(1),clock.now().plusSeconds(1500)); }
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
        Instant nextStart=service.workspace(owner).blocks().get(0).endTime().plusSeconds(2);
        org.mockito.Mockito.when(clock.now()).thenReturn(nextStart);
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
        mvc.perform(post("/fixed-commitments").header("Authorization","Bearer "+token).contentType("application/json").content("{\"title\":\"Meeting\",\"startTime\":\""+clock.now()+"\",\"endTime\":\""+clock.now().plusSeconds(600)+"\"}")).andExpect(status().isCreated());
        action(b,"start","fixed","{}").andExpect(status().isConflict());
        assertEquals(1,service.workspace(owner).fixed().size());
    }
    @Test void moveKeepsOldBlockAndReplaysWithoutDuplicate() {
        long t=task(),b=block(t);
        var moved=new ExecutionController.Placement(t,clock.now().plusSeconds(3600),clock.now().plusSeconds(5100));
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
    @Test void completeEndToEndExecutionJourney() throws Exception {
        // 1. Goal
        String goalRes=mvc.perform(post("/goals").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"title\":\"Launch Project Atlas\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long goalId=mapper.readTree(goalRes).path("id").asLong();

        // 2. Plan (Roadmap & Milestone)
        String roadRes=mvc.perform(post("/goals/"+goalId+"/roadmaps").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long roadmapId=mapper.readTree(roadRes).path("id").asLong();
        String mileRes=mvc.perform(post("/roadmaps/"+roadmapId+"/milestones").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"title\":\"Core Execution Engine\",\"order\":1}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long milestoneId=mapper.readTree(mileRes).path("id").asLong();

        // 3. Executable Tasks
        String t1Res=mvc.perform(post("/commitments").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"title\":\"Implement Work Window Persistence\",\"completionCriterion\":\"Tables and transitions tested\",\"goalId\":"+goalId+",\"milestoneId\":"+milestoneId+",\"importance\":\"high\",\"flexibilityTier\":\"fixed\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long task1=mapper.readTree(t1Res).path("id").asLong();
        String t2Res=mvc.perform(post("/commitments").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"title\":\"Build Focus Mode UI\",\"completionCriterion\":\"Focus timer ticks and pauses\",\"goalId\":"+goalId+",\"milestoneId\":"+milestoneId+",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long task2=mapper.readTree(t2Res).path("id").asLong();

        // 4. Open Today workspace
        mvc.perform(get("/execution").header("Authorization","Bearer "+token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.tasks[0].goalTitle").value("Launch Project Atlas"))
            .andExpect(jsonPath("$.tasks[0].milestoneTitle").value("Core Execution Engine"))
            .andExpect(jsonPath("$.blocks").isEmpty())
            .andExpect(jsonPath("$.history").isEmpty());

        // 5. Schedule Task 1
        String blockRes=mvc.perform(post("/schedule/blocks").header("Authorization","Bearer "+token).header("Idempotency-Key","e2e-place").contentType("application/json")
            .content("{\"commitmentId\":"+task1+",\"startTime\":\""+clock.now().minusSeconds(1)+"\",\"endTime\":\""+clock.now().plusSeconds(1800)+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("scheduled")).andReturn().getResponse().getContentAsString();
        long block1=mapper.readTree(blockRes).path("id").asLong();

        // 6. Start Task 1 -> Focus
        action(block1,"start","e2e-start","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("running"));
        assertEquals("in_progress",commitments.get(owner,task1).workState());

        // 7. Pause & Resume
        action(block1,"pause","e2e-pause","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("paused"));
        action(block1,"resume","e2e-resume","{}").andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("running"));

        // 8. Finish Task 1
        action(block1,"finish","e2e-finish","{\"report\":\"All integration tests pass and tables created.\",\"completionPct\":100}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.sessionState").value("finished"));
        assertEquals("completed",commitments.get(owner,task1).workState());

        // 9. Next work item visible in Today workspace
        var ws=service.workspace(owner);
        assertEquals(1,ws.history().size());
        assertEquals("Implement Work Window Persistence",ws.history().get(0).title());
        assertEquals("All integration tests pass and tables created.",ws.history().get(0).report());
        assertEquals(new BigDecimal("100.00"),ws.history().get(0).completionPct());

        // Task 1 is completed; Task 2 is ready and next
        assertEquals("completed",ws.tasks().stream().filter(t->t.id().equals(task1)).findFirst().get().workState());
        assertEquals("ready",ws.tasks().stream().filter(t->t.id().equals(task2)).findFirst().get().workState());

        // 10. Security / Cross-user isolation
        mvc.perform(get("/execution").header("Authorization","Bearer "+foreign))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty()).andExpect(jsonPath("$.history").isEmpty());
        mvc.perform(post("/blocks/"+block1+"/session/start").header("Authorization","Bearer "+foreign).header("Idempotency-Key","foreign-start"))
            .andExpect(status().isNotFound());
    }

    @Test void allLifecycleTransitionsAndRetriesPreserveFinalFacts() throws Exception {
        long b=block(task());
        for(String action: new String[]{"pause","resume","finish"}) action(b,action,"before-"+action,report(0)).andExpect(status().isConflict());
        String started=action(b,"start","start","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        action(b,"start","duplicate-start","{}").andExpect(status().isConflict());
        action(b,"resume","bad-resume","{}").andExpect(status().isConflict());
        var first=clock.now();
        org.mockito.Mockito.when(clock.now()).thenReturn(first.plusSeconds(600));
        String paused=action(b,"pause","pause","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.mockito.Mockito.when(clock.now()).thenReturn(first.plusSeconds(1200));
        assertEquals(paused,action(b,"pause","pause","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        action(b,"pause","duplicate-pause","{}").andExpect(status().isConflict());
        assertEquals(600000,service.workspace(owner).blocks().get(0).activeMillis());
        String resumed=action(b,"resume","resume","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.mockito.Mockito.when(clock.now()).thenReturn(first.plusSeconds(1800));
        assertEquals(resumed,action(b,"resume","resume","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String finished=action(b,"finish","finish",report(0)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var history=service.workspace(owner).history(); var finalBlock=service.workspace(owner).blocks();
        assertEquals(1200000,history.get(0).activeMillis());
        assertEquals(finished,action(b,"finish","finish",report(0)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(started,action(b,"start","start","{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(String action:new String[]{"start","pause","resume","finish"}) action(b,action,"after-"+action,report(0)).andExpect(status().isConflict());
        assertEquals(history,service.workspace(owner).history()); assertEquals(finalBlock,service.workspace(owner).blocks());
    }

    @Test void overrunClaimsExactlyAtFiveMinutesSurvivesRetriesAndNeverStopsWork() throws Exception {
        long b=block(task()); action(b,"start","start","{}").andExpect(status().isOk());
        Instant boundary=service.workspace(owner).blocks().get(0).endTime().plusSeconds(300);
        org.mockito.Mockito.when(clock.now()).thenReturn(boundary.minusNanos(1000));
        action(b,"overrun","before","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(false));
        org.mockito.Mockito.when(clock.now()).thenReturn(boundary);
        action(b,"overrun","at","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(true));
        org.mockito.Mockito.when(clock.now()).thenReturn(boundary.plusSeconds(30));
        action(b,"overrun","other-tab","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(false));
        action(b,"overrun","at","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(true));
        assertEquals(boundary,service.workspace(owner).blocks().get(0).overrunPromptedAt());
        assertEquals("running",service.workspace(owner).blocks().get(0).sessionState());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM events WHERE type='session.overrun_prompted'",Integer.class));
        action(b,"finish","finish",report(40)).andExpect(status().isOk());
        var history=service.workspace(owner).history();
        action(b,"overrun","finished","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(false));
        assertEquals(history,service.workspace(owner).history());
    }

    @Test void pausedOverrunWaitsUntilResumeAndConcurrentClaimsHaveOneWinner() throws Exception {
        long b=block(task()); service.transition(owner,b,"start","start",null); service.transition(owner,b,"pause","pause",null);
        Instant later=service.workspace(owner).blocks().get(0).endTime().plusSeconds(301);
        org.mockito.Mockito.when(clock.now()).thenReturn(later);
        assertFalse(mapper.readTree(service.overrun(owner,b,"paused")).path("showPrompt").asBoolean());
        service.transition(owner,b,"resume","resume",null);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->service.overrun(owner,b,"tab-a")); var c=pool.submit(()->service.overrun(owner,b,"tab-b"));
            assertNotEquals(mapper.readTree(a.get()).path("showPrompt").asBoolean(),mapper.readTree(c.get()).path("showPrompt").asBoolean());
        } finally {pool.shutdownNow();}
    }

    @Test void allExecutionMutationsRejectForeignAndUnauthenticatedCallers() throws Exception {
        long t=task(),b=block(t);
        for(String action:new String[]{"start","pause","resume","finish","overrun"}) {
            mvc.perform(post("/blocks/"+b+"/session/"+action).header("Idempotency-Key",action).contentType("application/json").content(report(50))).andExpect(status().isUnauthorized());
            mvc.perform(post("/blocks/"+b+"/session/"+action).header("Authorization","Bearer "+foreign).header("Idempotency-Key",action).contentType("application/json").content(report(50))).andExpect(status().isNotFound());
        }
        for(String path:new String[]{"/schedule/blocks","/schedule/blocks/"+b+"/move"})
            mvc.perform(post(path).header("Authorization","Bearer "+foreign).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(mapper.writeValueAsString(placement(t)))).andExpect(status().isNotFound());
        mvc.perform(get("/execution").header("Authorization","Bearer "+foreign)).andExpect(status().isOk())
            .andExpect(jsonPath("$.progress.plannedMillis").value(0)).andExpect(jsonPath("$.progress.executedMillis").value(0)).andExpect(jsonPath("$.progress.achieved").isEmpty());
    }

    @Test void invalidReportsNeverChangeHistoryOrBelief() throws Exception {
        long b=block(task()); service.transition(owner,b,"start","start",null);
        for(String value:new String[]{"-1","101","null","0.001"})
            action(b,"finish","invalid-"+value,"{\"report\":\"Work\",\"completionPct\":"+value+"}").andExpect(status().isBadRequest());
        action(b,"finish","missing","{}").andExpect(status().isBadRequest());
        assertTrue(service.workspace(owner).history().isEmpty());
        assertEquals("running",service.workspace(owner).blocks().get(0).sessionState());
    }

    @Test void manualMoveEnforcesHoursProtectionBuffersCapacityAndRollsBack() {
        long t=task(),b=block(t); var original=service.workspace(owner).blocks();
        Instant start=clock.now().plusSeconds(7200);
        db.update("DELETE FROM working_hours_config WHERE user_id=?",owner);
        assertThrows(ExecutionException.class,()->service.move(owner,b,"no-hours",new ExecutionController.Placement(t,start,start.plusSeconds(1800))));
        assertEquals(original,service.workspace(owner).blocks());
        for(int day=1;day<=7;day++) {
            db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'00:00:00','12:00:00','working')",owner,day);
            db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'12:00:00','00:00:00','working')",owner,day);
        }
        db.update("UPDATE scheduling_config SET workable_fraction=0 WHERE user_id=?",owner);
        assertThrows(ExecutionException.class,()->service.move(owner,b,"capacity",new ExecutionController.Placement(t,start,start.plusSeconds(1800))));
        assertEquals(original,service.workspace(owner).blocks());
        db.update("UPDATE scheduling_config SET workable_fraction=1,buffer_minutes=10 WHERE user_id=?",owner);
        db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(?,'Fixed',?,?,'manual')",owner,java.time.LocalDateTime.ofInstant(start.minusSeconds(1200),java.time.ZoneOffset.UTC),java.time.LocalDateTime.ofInstant(start.minusSeconds(300),java.time.ZoneOffset.UTC));
        assertThrows(ExecutionException.class,()->service.move(owner,b,"buffer",new ExecutionController.Placement(t,start,start.plusSeconds(1800))));
        assertEquals(original,service.workspace(owner).blocks());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency WHERE request_key IN ('no-hours','capacity','buffer')",Integer.class));
    }

    @Test void generatedMoveExecuteAndRegeneratePreserveStickyHistoryAndProgress() throws Exception {
        long t=task(); Instant start=clock.now().plusSeconds(60),end=start.plusSeconds(7200);
        String request="{\"startTime\":\""+start+"\",\"endTime\":\""+end+"\",\"work\":[{\"commitmentId\":"+t+",\"workMinutes\":30}]}";
        mvc.perform(post("/schedule/generate").header("Authorization","Bearer "+token).header("Idempotency-Key","generate").contentType("application/json").content(request)).andExpect(status().isOk());
        var generated=service.workspace(owner).blocks().get(0); assertFalse(generated.userMovedFlag());
        Instant movedStart=generated.startTime().plusSeconds(1800);
        long moved=mapper.readTree(service.move(owner,generated.id(),"move",new ExecutionController.Placement(t,movedStart,movedStart.plusSeconds(1800)))).path("id").asLong();
        var before=service.workspace(owner).blocks(); assertTrue(before.stream().filter(b->b.id()==moved).findFirst().orElseThrow().userMovedFlag());
        mvc.perform(post("/schedule/generate").header("Authorization","Bearer "+token).header("Idempotency-Key","regenerate").contentType("application/json").content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.placements").isEmpty());
        assertEquals(before,service.workspace(owner).blocks());
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart);
        action(moved,"start","start","{}").andExpect(status().isOk());
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart.plusSeconds(600)); action(moved,"pause","pause","{}").andExpect(status().isOk());
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart.plusSeconds(900)); action(moved,"resume","resume","{}").andExpect(status().isOk());
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart.plusSeconds(2100)); action(moved,"overrun","overrun","{}").andExpect(status().isOk()).andExpect(jsonPath("$.showPrompt").value(true));
        action(moved,"finish","finish",report(40)).andExpect(status().isOk());
        var ws=service.workspace(owner); assertEquals(1800000,ws.progress().plannedMillis()); assertEquals(1800000,ws.progress().executedMillis());
        assertEquals(new BigDecimal("40.00"),ws.progress().achieved().get(0).completionPct());
        var history=ws.history();
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart.plusSeconds(4000)); long second=block(t);
        service.transition(owner,second,"start","start-second",null);
        org.mockito.Mockito.when(clock.now()).thenReturn(movedStart.plusSeconds(4300)); service.transition(owner,second,"finish","finish-second",new ExecutionController.Report("Reassessed the work",new BigDecimal("30")));
        assertTrue(service.workspace(owner).history().containsAll(history));
        assertEquals(new BigDecimal("30.00"),service.workspace(owner).progress().achieved().get(0).completionPct());
    }

    @Test void overrunEventFailureRollsBackMarkerAndReplay() {
        long b=block(task()); service.transition(owner,b,"start","start",null);
        Instant boundary=service.workspace(owner).blocks().get(0).endTime().plusSeconds(300);
        org.mockito.Mockito.when(clock.now()).thenReturn(boundary);
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_execution_event CHECK(type <> 'session.overrun_prompted')");
        assertThrows(RuntimeException.class,()->service.overrun(owner,b,"prompt"));
        assertNull(service.workspace(owner).blocks().get(0).overrunPromptedAt());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency WHERE request_key='prompt'",Integer.class));
        db.execute("ALTER TABLE events DROP CONSTRAINT reject_execution_event");
        assertTrue(mapper.readTree(service.overrun(owner,b,"prompt")).path("showPrompt").asBoolean());
    }

    @Test void moveBackwardAcrossLocalDateHonorsProtectedHoursAndTimezone() {
        org.mockito.Mockito.when(clock.now()).thenReturn(Instant.parse("2026-09-26T18:00:00Z"));
        db.update("UPDATE scheduling_config SET timezone='Asia/Kolkata' WHERE user_id=?",owner);
        long t=task(); Instant start=Instant.parse("2026-09-26T19:00:00Z");
        long b=mapper.readTree(service.place(owner,"place",new ExecutionController.Placement(t,start,start.plusSeconds(1800)))).path("id").asLong();
        // Sunday 00:30 in the saved zone, independent of the JVM zone.
        db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,7,'00:30:00','01:00:00','protected')",owner);
        assertThrows(ExecutionException.class,()->service.move(owner,b,"protected",new ExecutionController.Placement(t,start,start.plusSeconds(1800))));
        Instant earlier=Instant.parse("2026-09-26T18:15:00Z");
        long moved=mapper.readTree(service.move(owner,b,"backward",new ExecutionController.Placement(t,earlier,earlier.plusSeconds(1800)))).path("id").asLong();
        var ws=service.workspace(owner); assertEquals("Asia/Kolkata",ws.timezone());
        assertEquals(earlier,ws.blocks().stream().filter(v->v.id()==moved).findFirst().orElseThrow().startTime());
        assertTrue(ws.blocks().stream().filter(v->v.id()==moved).findFirst().orElseThrow().userMovedFlag());
        db.update("UPDATE scheduling_config SET timezone='America/New_York' WHERE user_id=?",owner);
        assertEquals(ws.blocks(),service.workspace(owner).blocks());
    }

    @Test void sixtyMinuteSessionWithTenMinutePauseReportsFiftyExecutedAndPreservesHistory() {
        Instant start=Instant.parse("2026-09-27T09:00:00Z");
        org.mockito.Mockito.when(clock.now()).thenReturn(start);
        long t=task();
        long b=mapper.readTree(service.place(owner,"one-hour",new ExecutionController.Placement(t,start,start.plusSeconds(3600)))).path("id").asLong();
        service.transition(owner,b,"start","start",null);
        org.mockito.Mockito.when(clock.now()).thenReturn(start.plusSeconds(1200));
        service.transition(owner,b,"pause","pause",null);
        org.mockito.Mockito.when(clock.now()).thenReturn(start.plusSeconds(1800));
        service.transition(owner,b,"resume","resume",null);
        assertEquals(0,service.workspace(owner).progress().executedMillis());
        org.mockito.Mockito.when(clock.now()).thenReturn(start.plusSeconds(3600));
        service.transition(owner,b,"finish","finish",new ExecutionController.Report("First part done",new BigDecimal("40")));
        var ws=service.workspace(owner);
        assertEquals(3600000,ws.progress().plannedMillis());
        assertEquals(3000000,ws.progress().executedMillis());
        assertEquals(3600000,java.time.Duration.between(ws.history().get(0).startTime(),ws.history().get(0).endTime()).toMillis());
        assertEquals(3000000,ws.history().get(0).activeMillis());
        var facts=db.queryForList("SELECT * FROM actual_sessions WHERE scheduled_block_id=?",b);
        var runtime=db.queryForList("SELECT * FROM focus_sessions WHERE scheduled_block_id=?",b);
        var transitions=db.queryForList("SELECT * FROM events WHERE entity_type='scheduled_block' AND entity_id=? AND type IN ('session.paused','session.resumed') ORDER BY id",b);
        assertEquals(2,transitions.size());
        org.mockito.Mockito.when(clock.now()).thenReturn(start.plusSeconds(7200));
        long next=block(t); service.transition(owner,next,"start","next-start",null);
        org.mockito.Mockito.when(clock.now()).thenReturn(start.plusSeconds(7500));
        service.transition(owner,next,"finish","next-finish",new ExecutionController.Report("Revised estimate",new BigDecimal("30")));
        assertEquals(3300000,service.workspace(owner).progress().executedMillis());
        assertEquals(new BigDecimal("30.00"),service.workspace(owner).progress().achieved().get(0).completionPct());
        assertEquals(facts,db.queryForList("SELECT * FROM actual_sessions WHERE scheduled_block_id=?",b));
        assertEquals(runtime,db.queryForList("SELECT * FROM focus_sessions WHERE scheduled_block_id=?",b));
        assertEquals(transitions,db.queryForList("SELECT * FROM events WHERE entity_type='scheduled_block' AND entity_id=? AND type IN ('session.paused','session.resumed') ORDER BY id",b));
    }
}
