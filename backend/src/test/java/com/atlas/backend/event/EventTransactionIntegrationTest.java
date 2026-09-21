package com.atlas.backend.event;

import static org.junit.jupiter.api.Assertions.*;

import com.atlas.backend.commitment.CommitmentRequest;
import com.atlas.backend.commitment.CommitmentService;
import com.atlas.backend.dependency.DependencyService;
import com.atlas.backend.fixedcommitment.CreateFixedCommitmentRequest;
import com.atlas.backend.fixedcommitment.FixedCommitmentService;
import com.atlas.backend.goal.*;
import com.atlas.backend.recurringintention.CreateRecurringIntentionRequest;
import com.atlas.backend.recurringintention.RecurringIntentionService;
import com.atlas.backend.task.CreateTaskRequest;
import com.atlas.backend.task.TaskService;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** No test-level transaction: every final JDBC assertion observes committed database state. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:evt002;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class EventTransactionIntegrationTest {
    @Autowired EventRepository events;
    @Autowired TaskService tasks;
    @Autowired GoalService goals;
    @Autowired CommitmentService commitments;
    @Autowired RecurringIntentionService recurring;
    @Autowired FixedCommitmentService fixed;
    @Autowired DependencyService dependencies;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    final ObjectMapper mapper = new ObjectMapper();
    Long owner;

    @BeforeEach void setup() { owner = users.save(User.of("evt002@example.com", "test")).getId(); }
    @AfterEach void cleanup() {
        jdbc.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_evt002_reject_event");
        for (String table : List.of("events", "commitment_dependency", "commitments", "fixed_commitments",
                "recurring_intentions", "goals", "tasks", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    enum Mutation { TASK_START, GOAL_CREATE, GOAL_UPDATE, GOAL_COMPLETE, COMMITMENT_UPDATE, RECURRING_COMPLETE, FIXED_DELETE, DEPENDENCY_ADD }
    record Scenario(Runnable mutate, String stateQuery, String eventType) { }

    Scenario scenario(Mutation mutation) {
        return switch (mutation) {
            case TASK_START -> {
                long id = tasks.create(owner, new CreateTaskRequest("Task")).id();
                yield new Scenario(() -> tasks.start(owner, id), "SELECT status FROM tasks WHERE id=" + id, "task.started");
            }
            case GOAL_CREATE -> new Scenario(() -> goals.create(owner, new CreateGoalRequest("Goal", null, null)),
                    "SELECT title FROM goals ORDER BY id", "goal.created");
            case GOAL_UPDATE, GOAL_COMPLETE -> {
                long id = goals.create(owner, new CreateGoalRequest("Goal", null, null)).id();
                UpdateGoalRequest request = new UpdateGoalRequest();
                request.setTitle("Updated");
                yield mutation == Mutation.GOAL_UPDATE
                    ? new Scenario(() -> goals.update(owner, id, request), "SELECT title FROM goals WHERE id=" + id, "goal.updated")
                    : new Scenario(() -> goals.complete(owner, id), "SELECT lifecycle_state FROM goals WHERE id=" + id, "goal.completed");
            }
            case COMMITMENT_UPDATE -> {
                long id = commitment();
                yield new Scenario(() -> commitments.update(owner, id,
                    mapper.readValue("{\"title\":\"Updated\"}", CommitmentRequest.class)),
                    "SELECT title FROM commitments WHERE id=" + id, "task.updated");
            }
            case RECURRING_COMPLETE -> {
                long id = recurring.create(owner, new CreateRecurringIntentionRequest(null, "Practice", 3, null, "flexible")).id();
                yield new Scenario(() -> recurring.completeInstance(owner, id),
                    "SELECT current_week_remaining_count FROM recurring_intentions WHERE id=" + id, "recurring_intention.instance_completed");
            }
            case FIXED_DELETE -> {
                long id = fixed.create(owner, mapper.readValue("{\"title\":\"Meeting\",\"startTime\":\"2026-10-01T10:00:00Z\",\"endTime\":\"2026-10-01T11:00:00Z\"}",
                    CreateFixedCommitmentRequest.class)).id();
                yield new Scenario(() -> fixed.delete(owner, id), "SELECT id FROM fixed_commitments WHERE id=" + id, "fixed_commitment.deleted");
            }
            case DEPENDENCY_ADD -> {
                long blocked = commitment();
                long blocking = commitment();
                yield new Scenario(() -> dependencies.add(owner, blocked, blocking),
                    "SELECT blocking_commitment_id, blocked_commitment_id FROM commitment_dependency ORDER BY id", "task.dependency_added");
            }
        };
    }

    long commitment() {
        return commitments.create(owner, mapper.readValue("{\"title\":\"Task\",\"importance\":\"high\",\"flexibilityTier\":\"flexible\"}", CommitmentRequest.class)).id();
    }
    List<Map<String, Object>> history() { return jdbc.queryForList("SELECT * FROM events ORDER BY id"); }

    @ParameterizedTest @EnumSource(Mutation.class)
    void successfulMutationAndEventCommitTogether(Mutation mutation) {
        Scenario scenario = scenario(mutation);
        var before = jdbc.queryForList(scenario.stateQuery());
        int eventCount = history().size();
        scenario.mutate().run();
        assertNotEquals(before, jdbc.queryForList(scenario.stateQuery()));
        assertEquals(eventCount + 1, history().size());
        assertEquals(scenario.eventType(), jdbc.queryForObject("SELECT type FROM events ORDER BY id DESC LIMIT 1", String.class));
    }

    @ParameterizedTest @EnumSource(Mutation.class)
    void eventDatabaseFailureRollsBackMutationAndPreservesEarlierHistory(Mutation mutation) {
        Scenario scenario = scenario(mutation);
        var before = jdbc.queryForList(scenario.stateQuery());
        var history = history();
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_evt002_reject_event CHECK(type <> '" + scenario.eventType() + "')");
        assertThrows(DataIntegrityViolationException.class, scenario.mutate()::run);
        assertEquals(before, jdbc.queryForList(scenario.stateQuery()));
        assertEquals(history, history());
    }

    @ParameterizedTest @EnumSource(Mutation.class)
    void surroundingDatabaseFailureRollsBackAlreadyFlushedEventAndMutation(Mutation mutation) {
        Scenario scenario = scenario(mutation);
        var before = jdbc.queryForList(scenario.stateQuery());
        var history = history();
        assertThrows(DataIntegrityViolationException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            scenario.mutate().run();
            entityManager.flush();
            assertNotEquals(before, jdbc.queryForList(scenario.stateQuery()));
            assertEquals(history.size() + 1, history().size());
            // A real later database write fails after both paired writes have reached SQL.
            jdbc.update("UPDATE users SET email = NULL WHERE id = ?", owner);
        }));
        assertEquals(before, jdbc.queryForList(scenario.stateQuery()));
        assertEquals(history, history());
        assertEquals("evt002@example.com", jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, owner));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void appendCannotStartAnIndependentTransaction(boolean flush) {
        assertThrows(IllegalTransactionStateException.class, () -> append(flush, Event.forEntity("goal", 99L, "goal.created", "user")));
        assertEquals(List.of(), history());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void appendRejectsReadOnlyTransactions(boolean flush) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        var failure = assertThrows(InvalidDataAccessApiUsageException.class, () -> readOnly.executeWithoutResult(status ->
                append(flush, Event.forEntity("goal", 99L, "goal.created", "user"))));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(List.of(), history());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void persistedEventCannotBeAppendedAsAnUpdate(boolean flush) {
        goals.create(owner, new CreateGoalRequest("Goal", null, null));
        var before = history();
        Event existing = events.findAll().get(0);
        ReflectionTestUtils.setField(existing, "reason", "attempted rewrite");
        var failure = assertThrows(InvalidDataAccessApiUsageException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> append(flush, existing)));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(before, history());
    }

    @Test void failureBeforeEventInsertLeavesNeitherEntityNorEvent() {
        assertThrows(DataIntegrityViolationException.class, () -> goals.create(owner, new CreateGoalRequest(null, null, null)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM goals", Integer.class));
        assertEquals(List.of(), history());
    }

    @Test void caughtEventFailureStillMarksTheCallerTransactionForRollback() {
        long id = goals.create(owner, new CreateGoalRequest("Goal", null, null)).id();
        var before = history();
        jdbc.execute("ALTER TABLE events ADD CONSTRAINT chk_evt002_reject_event CHECK(type <> 'goal.completed')");
        assertThrows(UnexpectedRollbackException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThrows(DataIntegrityViolationException.class, () -> goals.complete(owner, id));
        }));
        assertEquals("active", jdbc.queryForObject("SELECT lifecycle_state FROM goals WHERE id=?", String.class, id));
        assertEquals(before, history());
    }

    @Test void unchangedGoalPatchDoesNotCreateAnEvent() {
        long id = goals.create(owner, new CreateGoalRequest("Goal", null, null)).id();
        var before = history();
        goals.update(owner, id, new UpdateGoalRequest());
        assertEquals(before, history());
    }

    void append(boolean flush, Event event) {
        if (flush) events.appendAndFlush(event); else events.append(event);
    }
}
