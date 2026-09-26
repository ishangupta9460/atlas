package com.atlas.backend.scheduling;

import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:scheduling_pipeline;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class SchedulingPipelineIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @Autowired SchedulingPipeline pipeline;
    @Autowired WorkingHoursService hours;
    MockMvc mvc;
    Long owner, other;
    String token;
    ObjectMapper mapper = new ObjectMapper();
    static final Instant START=Instant.parse("2026-09-25T09:00:00Z");
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user=users.save(User.of(UUID.randomUUID()+"@test.example","unused"));owner=user.getId();token=jwt.generateToken(user);
        other=users.save(User.of(UUID.randomUUID()+"@test.example","unused")).getId();
        hours.put(owner,new WorkingHours("UTC",List.of(new WorkingHours.Window(5,LocalTime.of(9,0),LocalTime.of(17,0),"working"))));
    }
    @AfterEach void cleanup() {
        db.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS reject_generated");
        db.execute("ALTER TABLE execution_idempotency DROP CONSTRAINT IF EXISTS reject_generate_replay");
    }
    long work(Long user,String importance,String flexibility) {
        db.update("INSERT INTO commitments(user_id,title,completion_criterion,importance,flexibility_tier,work_state) VALUES(?,'Work','Done',?,?,'ready')",user,importance,flexibility);
        return db.queryForObject("SELECT MAX(id) FROM commitments WHERE user_id=?",Long.class,user);
    }
    SchedulingPipeline.Request request(long... ids) {
        return new SchedulingPipeline.Request(START,START.plusSeconds(28800),Arrays.stream(ids).mapToObj(id->new SchedulingPipeline.WorkInput(id,30)).toList(),null);
    }
    ResultActions generate(String key,SchedulingPipeline.Request request) throws Exception {
        return mvc.perform(post("/schedule/generate").header("Authorization","Bearer "+token).header("Idempotency-Key",key)
                .contentType("application/json").content(mapper.writeValueAsString(request)));
    }
    @Test void placesExplainsAuditsAndReplaysWithoutDuplicate() throws Exception {
        long a=work(owner,"low","flexible"),b=work(owner,"critical","flexible");
        String result=generate("first",request(a,b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].decision.commitmentId").value(b))
                .andExpect(jsonPath("$.placements.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertEquals(result,generate("first",request(b,a)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM events e JOIN scheduled_blocks b ON e.entity_id=b.id WHERE b.user_id=? AND e.type='block.generated' AND e.actor='atlas' AND e.reason=b.placement_reason",Integer.class,owner));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND user_moved_flag=TRUE",Integer.class,owner));
        generate("fresh",request(a,b)).andExpect(status().isOk()).andExpect(jsonPath("$.placements").isEmpty());
        generate("first",request(a)).andExpect(status().isConflict());
    }
    @Test void authenticationValidationAndOwnership() throws Exception {
        long a=work(owner,"medium","flexible"),foreign=work(other,"critical","flexible");
        mvc.perform(post("/schedule/generate").contentType("application/json").content(mapper.writeValueAsString(request(a)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/schedule/generate").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsString(request(a)))).andExpect(status().isBadRequest());
        generate("foreign",request(foreign)).andExpect(status().isNotFound());
        generate("missing",request(Long.MAX_VALUE)).andExpect(status().isNotFound());
        generate("duplicate",request(a,a)).andExpect(status().isBadRequest());
        generate("duration",new SchedulingPipeline.Request(START,START.plusSeconds(3600),List.of(new SchedulingPipeline.WorkInput(a,0)),null)).andExpect(status().isBadRequest());
        generate("instruction",new SchedulingPipeline.Request(START,START.plusSeconds(3600),request(a).work(),foreign)).andExpect(status().isBadRequest());
        mvc.perform(post("/schedule/generate").header("Authorization","Bearer "+token).header("Idempotency-Key","injected")
                .contentType("application/json").content(mapper.writeValueAsString(request(a)).replaceFirst("\\{","{\"userId\":"+other+","))).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,other));
    }
    @Test void dependenciesAndFixedAreProtectedAndOverrideIsScoped() throws Exception {
        long a=work(owner,"critical","flexible"),b=work(owner,"low","flexible"),blocked=work(owner,"critical","flexible"),fixed=work(owner,"critical","fixed");
        db.update("INSERT INTO commitment_dependency(blocking_commitment_id,blocked_commitment_id) VALUES(?,?)",a,blocked);
        db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(?,'Meeting',?,?,'manual')",owner,LocalDateTime.parse("2026-09-25T09:00:00"),LocalDateTime.parse("2026-09-25T10:00:00"));
        var r=request(a,b,blocked,fixed);
        generate("override",new SchedulingPipeline.Request(r.startTime(),r.endTime(),r.work(),b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].decision.commitmentId").value(b))
                .andExpect(jsonPath("$.placements[0].decision.startTime").value("2026-09-25T10:10:00Z"))
                .andExpect(jsonPath("$.placements.length()").value(2));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM fixed_commitments WHERE user_id=? AND start_time=?",Integer.class,owner,LocalDateTime.parse("2026-09-25T09:00:00")));
    }
    @Test void emptyAndPackedCalendarReturnEmptyPlans() throws Exception {
        generate("empty",request()).andExpect(status().isOk()).andExpect(jsonPath("$.placements").isEmpty());
        long a=work(owner,"medium","flexible");
        db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(?,'Busy',?,?,'manual')",owner,LocalDateTime.parse("2026-09-25T09:00:00"),LocalDateTime.parse("2026-09-25T17:00:00"));
        generate("packed",request(a)).andExpect(status().isOk()).andExpect(jsonPath("$.placements").isEmpty());
    }
    @Test void eventFailureRollsBackBlocksAndReplayRecord() throws Exception {
        long a=work(owner,"medium","flexible");
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_generated CHECK(type <> 'block.generated' OR payload NOT LIKE '%\"commitmentId\":"+a+",%')");
        generate("failure",request(a)).andExpect(status().isInternalServerError());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency WHERE user_id=?",Integer.class,owner));
    }
    @Test void failureAfterEventRollsBackWholeBatch() throws Exception {
        long a=work(owner,"medium","flexible"),b=work(owner,"medium","flexible");
        db.execute("ALTER TABLE execution_idempotency ADD CONSTRAINT reject_generate_replay CHECK(user_id <> "+owner+")");
        generate("failure",request(a,b)).andExpect(status().isInternalServerError());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM events WHERE type='block.generated' AND payload LIKE ?",Integer.class,"%\"commitmentId\":"+a+",%"));
    }
    @Test void concurrentRetriesAndDistinctKeysDoNotDoublePlace() throws Exception {
        long a=work(owner,"medium","flexible");
        var executor=Executors.newFixedThreadPool(2);
        try {
            var latch=new CountDownLatch(1);
            Callable<String> call=()->{latch.await();return pipeline.generate(owner,"same",request(a));};
            var first=executor.submit(call);var second=executor.submit(call);latch.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            long b=work(owner,"medium","flexible");
            var x=executor.submit(()->pipeline.generate(owner,"x",request(b)));
            var y=executor.submit(()->pipeline.generate(owner,"y",request(b)));
            x.get(20,TimeUnit.SECONDS);y.get(20,TimeUnit.SECONDS);
            assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        } finally {executor.shutdownNow();}
    }
    @Test void inactiveGoalsAreNotScheduledAndRiskBoostIsIntegrated() throws Exception {
        long a=work(owner,"medium","flexible"),b=work(owner,"medium","flexible"),c=work(owner,"medium","flexible");
        db.update("INSERT INTO goals(user_id,title,lifecycle_state,planning_state) VALUES(?,'Paused','active','paused')",owner);
        long paused=db.queryForObject("SELECT MAX(id) FROM goals WHERE user_id=?",Long.class,owner);
        db.update("UPDATE commitments SET goal_id=? WHERE id=?",paused,a);
        db.update("INSERT INTO goals(user_id,title,lifecycle_state,planning_state) VALUES(?,'Risk','active','at_risk')",owner);
        long risk=db.queryForObject("SELECT MAX(id) FROM goals WHERE user_id=?",Long.class,owner);
        db.update("UPDATE commitments SET goal_id=? WHERE id=?",risk,c);
        generate("goals",request(a,b,c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].decision.commitmentId").value(c))
                .andExpect(jsonPath("$.placements.length()").value(2)).andExpect(jsonPath("$.unplaced[0]").value(a));
    }
    @Test void fiftyItemsCompleteWithinTwoSecondsAfterWarmup() {
        pipeline.generate(owner,"warmup",request());
        long[] ids=new long[50];for(int i=0;i<50;i++) ids[i]=work(owner,"medium","flexible");
        long start=System.nanoTime();
        assertTimeout(Duration.ofSeconds(2),()->pipeline.generate(owner,"performance",request(ids)));
        System.out.println("CHUNK2_50_ITEMS_MS="+(System.nanoTime()-start)/1_000_000);
    }
}
