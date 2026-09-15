package com.atlas.backend.commitment;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.*;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=${dom003.transactions.url:jdbc:h2:mem:dom003_transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
    "spring.datasource.username=${dom003.mysql.user:sa}", "spring.datasource.password=${dom003.mysql.password:}",
    "spring.datasource.driver-class-name=${dom003.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class CommitmentTransactionIntegrationTest {
    @Autowired CommitmentService service;
    @Autowired CommitmentRepository repository;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    final ObjectMapper mapper=new ObjectMapper();
    Long owner;
    boolean reject;
    @BeforeEach void setup() { owner=users.save(User.of("dom003-transaction@example.com","test")).getId(); }
    @AfterEach void cleanup() {
        if(reject) jdbc.execute("ALTER TABLE events DROP CONSTRAINT chk_dom003_event");
        jdbc.update("DELETE FROM events"); jdbc.update("DELETE FROM commitments"); jdbc.update("DELETE FROM users");
    }
    CommitmentRequest request(String json) { return mapper.readValue(json,CommitmentRequest.class); }
    long create(boolean ready) { return service.create(owner,request("{\"importance\":\"high\",\"flexibilityTier\":\"flexible\",\"title\":\"Task\""+(ready?",\"completionCriterion\":\"Done\"":"")+"}")).id(); }
    void reject(String type) {
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_dom003_event CHECK(type <> '"+type+"')"); reject=true;
    }
    @Test void eventFailureRollsBackCreation() {
        reject("task.created"); assertThrows(RuntimeException.class,()->create(false));
        assertEquals(0,repository.count()); assertEquals(0,events.count());
    }
    @Test void secondCreationEventFailureRollsBackBothEventsAndEntity() {
        reject("task.ready"); assertThrows(RuntimeException.class,()->create(true));
        assertEquals(0,repository.count()); assertEquals(0,events.count());
    }
    @Test void updateReadinessFailureRollsBackFieldUpdateAndEarlierEvent() {
        long id=create(false); reject("task.ready");
        assertThrows(RuntimeException.class,()->service.update(owner,id,request("{\"completionCriterion\":\"Done\"}")));
        assertNull(service.get(owner,id).completionCriterion()); assertEquals("draft",service.get(owner,id).workState()); assertEquals(1,events.count());
    }
    @Test void ordinaryUpdateFailureRollsBack() {
        long id=create(false); reject("task.updated");
        assertThrows(RuntimeException.class,()->service.update(owner,id,request("{\"title\":\"Changed\"}")));
        assertEquals("Task",service.get(owner,id).title()); assertEquals(1,events.count());
    }
    @Test void transitionsWriteEventsAndFailureRollsBack() {
        long id=create(true);
        service.transition(owner,id,"in_progress",true,true);
        service.transition(owner,id,"ready",false,true);
        service.transition(owner,id,"in_progress",true,true);
        reject("task.completed");
        assertThrows(RuntimeException.class,()->service.transition(owner,id,"completed",false,true));
        assertEquals("in_progress",service.get(owner,id).workState());
        assertEquals(java.util.List.of("task.created","task.ready","task.started","task.partial","task.started"),jdbc.queryForList("SELECT type FROM events ORDER BY id",String.class));
        jdbc.execute("ALTER TABLE events DROP CONSTRAINT chk_dom003_event"); reject=false;
        service.transition(owner,id,"completed",false,true);
        assertEquals("completed",service.get(owner,id).workState());
        assertThrows(CommitmentException.class,()->service.transition(owner,id,"ready",true,true));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"task.started","task.partial"})
    void startAndPartialFailuresRollBack(String type) {
        long id=create(true);
        if (type.equals("task.partial")) service.transition(owner,id,"in_progress",true,true);
        String before=service.get(owner,id).workState(); long count=events.count();
        reject(type);
        String target=type.equals("task.started")?"in_progress":"ready";
        assertThrows(RuntimeException.class,()->service.transition(owner,id,target,true,true));
        assertEquals(before,service.get(owner,id).workState()); assertEquals(count,events.count());
    }
    @Test void concurrentUpdatesSerializeAndCaptureLatestSnapshot() throws Exception {
        long id=create(false);
        var changed=new CountDownLatch(1); var release=new CountDownLatch(1); var secondStarted=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->new TransactionTemplate(tm).executeWithoutResult(s->{
                service.update(owner,id,request("{\"title\":\"First\"}")); changed.countDown(); await(release);
            }));
            assertTrue(changed.await(10,TimeUnit.SECONDS));
            var second=pool.submit(()->{ secondStarted.countDown(); service.update(owner,id,request("{\"description\":\"Second\"}")); });
            assertTrue(secondStarted.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->second.get(250,TimeUnit.MILLISECONDS));
            release.countDown(); first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
            assertEquals("First",service.get(owner,id).title()); assertEquals("Second",service.get(owner,id).description());
            var payload=mapper.readTree(jdbc.queryForObject("SELECT payload FROM events WHERE type='task.updated' ORDER BY id DESC LIMIT 1",String.class));
            assertEquals("First",payload.path("before").path("title").asString());
        } finally { release.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("Transaction coordination timeout"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
}
