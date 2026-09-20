package com.atlas.backend.fixedcommitment;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${dom006.transactions.url:jdbc:h2:mem:dom006_transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
        "spring.datasource.username=${dom006.mysql.user:sa}",
        "spring.datasource.password=${dom006.mysql.password:}",
        "spring.datasource.driver-class-name=${dom006.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class FixedCommitmentTransactionIntegrationTest {
    @Autowired FixedCommitmentService service;
    @Autowired FixedCommitmentRepository repository;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    final ObjectMapper mapper = new ObjectMapper();
    Long owner;
    boolean eventConstraintAdded;

    @BeforeEach void setup() { owner = users.save(User.of("fixed-transaction@example.com", "not-used")).getId(); }
    @AfterEach void cleanup() {
        if (eventConstraintAdded) jdbc.execute("ALTER TABLE events DROP CONSTRAINT chk_dom006_reject_event");
        events.deleteAllForTest(); repository.deleteAll(); users.deleteAll();
    }

    @Test void eventFailureRollsBackCreation() {
        rejectEvent("created");
        assertThrows(DataIntegrityViolationException.class, this::create);
        assertEquals(0, repository.count()); assertEquals(0, events.count());
    }

    @Test void eventFailureRollsBackUpdateAndPreservesHistory() {
        long id = create();
        String original = events.findAll().get(0).getPayload();
        rejectEvent("updated");
        assertThrows(DataIntegrityViolationException.class, () -> service.update(owner, id, update("{\"title\":\"Changed\"}")));
        assertEquals("Meeting", service.get(owner, id).title());
        assertEquals(1, events.count()); assertEquals(original, events.findAll().get(0).getPayload());
    }

    @Test void eventFailureRollsBackPhysicalDelete() {
        long id = create();
        rejectEvent("deleted");
        assertThrows(DataIntegrityViolationException.class, () -> service.delete(owner, id));
        assertEquals("Meeting", service.get(owner, id).title());
        assertEquals(1, events.count());
    }

    @Test void concurrentPartialUpdatesWaitForOwnedLockAndCaptureLatestState() throws Exception {
        long id = create();
        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.update(owner, id, update("{\"title\":\"First edit\"}"));
                firstChanged.countDown();
                await(allowCommit);
            }));
            assertTrue(firstChanged.await(5, TimeUnit.SECONDS));
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                service.update(owner, id, update("{\"endTime\":\"2026-09-14T12:00:00Z\"}"));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            assertEquals("First edit", service.get(owner, id).title());
            assertEquals("2026-09-14T12:00:00Z", service.get(owner, id).endTime().toString());
            var updates = events.findAll().stream().filter(e -> e.getType().equals("fixed_commitment.updated")).toList();
            assertEquals(2, updates.size());
            var secondPayload = updates.stream().map(e -> mapper.readTree(e.getPayload()))
                    .filter(n -> n.path("after").path("endTime").asText().equals("2026-09-14T12:00:00Z")).findFirst().orElseThrow();
            assertEquals("First edit", secondPayload.path("before").path("title").asText());
        } finally { allowCommit.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void deleteWaitsForUpdateAndSnapshotsCommittedEdit() throws Exception {
        long id = create();
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.update(owner, id, update("{\"title\":\"Last title\"}"));
                changed.countDown(); await(allowCommit);
            }));
            assertTrue(changed.await(5, TimeUnit.SECONDS));
            Future<?> delete = pool.submit(() -> service.delete(owner, id));
            assertThrows(TimeoutException.class, () -> delete.get(200, TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            update.get(5, TimeUnit.SECONDS); delete.get(5, TimeUnit.SECONDS);
            assertFalse(repository.existsById(id));
            var deletion = events.findAll().stream().filter(e -> e.getType().equals("fixed_commitment.deleted")).findFirst().orElseThrow();
            assertEquals("Last title", mapper.readTree(deletion.getPayload()).path("before").path("title").asText());
            assertEquals(3, events.count());
        } finally { allowCommit.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    private long create() { return service.create(owner, mapper.readValue(FixedCommitmentIntegrationTest.BODY, CreateFixedCommitmentRequest.class)).id(); }
    private UpdateFixedCommitmentRequest update(String json) { return mapper.readValue(json, UpdateFixedCommitmentRequest.class); }
    private void rejectEvent(String action) {
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_dom006_reject_event CHECK (type <> 'fixed_commitment." + action + "')");
        eventConstraintAdded = true;
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transaction coordination"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
}
