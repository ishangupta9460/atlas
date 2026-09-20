package com.atlas.backend.goal;

import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import com.atlas.backend.event.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves real Event Log insert failures roll back paired Goal state mutations. */
@SpringBootTest
@ActiveProfiles("test")
class GoalTransactionIntegrationTest {
    @Autowired private GoalService goalService;
    @Autowired private GoalRepository goalRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_reject_goal_transition");
        eventRepository.deleteAllForTest();
        goalRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void realEventInsertFailureRollsBackLifecycleAndPlanningTransitions() {
        User user = userRepository.save(User.of("goal-transaction@example.com", "not-used"));
        Goal abandonable = goalRepository.save(Goal.create(user.getId(), "Lifecycle goal", null, null));
        Goal pausable = goalRepository.save(Goal.create(user.getId(), "Planning goal", null, null));
        goalService.markAtRisk(user.getId(), pausable.getId());

        jdbcTemplate.execute("ALTER TABLE events ADD CONSTRAINT chk_events_reject_goal_transition "
                + "CHECK (type NOT IN ('goal.abandoned', 'goal.paused'))");

        assertThrows(DataIntegrityViolationException.class, () -> goalService.abandon(user.getId(), abandonable.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> goalService.pause(user.getId(), pausable.getId()));

        assertEquals(Goal.ACTIVE, goalRepository.findById(abandonable.getId()).orElseThrow().getLifecycleState());
        assertEquals(Goal.AT_RISK, goalRepository.findById(pausable.getId()).orElseThrow().getPlanningState());
        assertEquals(0, countEvents(abandonable.getId(), "goal.abandoned"));
        assertEquals(0, countEvents(pausable.getId(), "goal.paused"));
    }

    private int countEvents(Long goalId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE entity_type = 'goal' AND entity_id = ? AND type = ?",
                Integer.class, goalId, type);
        return count == null ? 0 : count;
    }
}
