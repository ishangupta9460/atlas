package com.atlas.backend.dependency;

import static org.junit.jupiter.api.Assertions.*;

import com.atlas.backend.commitment.CommitmentRequest;
import com.atlas.backend.commitment.CommitmentService;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=${dom007.transactions.url:jdbc:h2:mem:dom007_transactions;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
    "spring.datasource.username=${dom007.mysql.user:sa}",
    "spring.datasource.password=${dom007.mysql.password:}",
    "spring.datasource.driver-class-name=${dom007.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class DependencyTransactionIntegrationTest {
    @Autowired DependencyService service;
    @Autowired CommitmentService commitments;
    @Autowired CommitmentDependencyRepository dependencies;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper mapper = new ObjectMapper();
    Long owner;
    boolean reject;

    @BeforeEach void setup() {
        owner = users.save(User.of("dom007-transaction@example.com", "test")).getId();
    }

    @AfterEach void cleanup() {
        if (reject) jdbc.execute("ALTER TABLE events DROP CONSTRAINT chk_dom007_event");
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM commitment_dependency");
        jdbc.update("DELETE FROM commitments");
        jdbc.update("DELETE FROM users");
    }

    long commitment() {
        return commitments.create(owner, mapper.readValue("{\"importance\":\"high\",\"flexibilityTier\":\"flexible\",\"title\":\"T\",\"completionCriterion\":\"Done\"}", CommitmentRequest.class)).id();
    }

    void reject(String type) {
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_dom007_event CHECK(type <> '" + type + "')");
        reject = true;
    }

    @Test void eventFailureRollsBackEdge() {
        long blocked = commitment();
        long blocking = commitment();
        reject("task.dependency_added");
        assertThrows(RuntimeException.class, () -> service.add(owner, blocked, blocking));
        assertEquals(0, dependencies.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE type LIKE 'task.dependency%'", Integer.class));
    }

    @Test void removeEventFailureRollsBackDelete() {
        long blocked = commitment();
        long blocking = commitment();
        service.add(owner, blocked, blocking);
        reject("task.dependency_removed");
        assertThrows(RuntimeException.class, () -> service.remove(owner, blocked, blocking));
        assertEquals(1, dependencies.count());
    }

    @Test void concurrentReciprocalInsertsKeepAcyclicGraph() throws Exception {
        long a = commitment();
        long b = commitment();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var conflicts = new AtomicInteger();
        var created = new AtomicInteger();
        try {
            var first = pool.submit(() -> {
                await(start);
                try {
                    if (service.add(owner, b, a).created()) created.incrementAndGet();
                } catch (DependencyException ex) {
                    if (ex.status() == 409) conflicts.incrementAndGet();
                    else throw ex;
                }
            });
            var second = pool.submit(() -> {
                await(start);
                try {
                    if (service.add(owner, a, b).created()) created.incrementAndGet();
                } catch (DependencyException ex) {
                    if (ex.status() == 409) conflicts.incrementAndGet();
                    else throw ex;
                }
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, created.get());
            assertEquals(1, conflicts.get());
            assertEquals(1, dependencies.count());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
