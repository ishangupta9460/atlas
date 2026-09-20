package com.atlas.backend.recurringintention;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.goal.GoalRepository;
import com.atlas.backend.roadmap.MilestoneRepository;
import com.atlas.backend.roadmap.RoadmapRepository;
import com.atlas.backend.task.TaskRepository;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves a real database Event Log failure rolls back the paired completion-count update. */
@SpringBootTest
@ActiveProfiles("test")
class RecurringIntentionTransactionIntegrationTest {
    @Autowired private RecurringIntentionService recurringIntentionService;
    @Autowired private RecurringIntentionRepository recurringIntentionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoadmapRepository roadmapRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach void cleanDatabase() {
        jdbcTemplate.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_reject_recurring_completion");
        jdbcTemplate.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_reject_recurring_creation");
        eventRepository.deleteAllForTest(); recurringIntentionRepository.deleteAll(); milestoneRepository.deleteAll(); roadmapRepository.deleteAll(); goalRepository.deleteAll(); taskRepository.deleteAll(); userRepository.deleteAll();
    }

    @Test
    void realEventInsertFailureRollsBackCompletedInstanceCount() {
        User user = userRepository.save(User.of("recurring-transaction@example.com", "not-used"));
        RecurringIntention intention = recurringIntentionRepository.save(RecurringIntention.create(user.getId(), null, "Practice", 2, null, "flexible"));
        jdbcTemplate.execute("ALTER TABLE events ADD CONSTRAINT chk_events_reject_recurring_completion CHECK (type <> 'recurring_intention.instance_completed')");

        assertThrows(DataIntegrityViolationException.class, () -> recurringIntentionService.completeInstance(user.getId(), intention.getId()));

        assertEquals(2, recurringIntentionRepository.findById(intention.getId()).orElseThrow().getCurrentWeekRemainingCount());
        Integer events = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type = 'recurring_intention' AND entity_id = ? AND type = ?", Integer.class, intention.getId(), "recurring_intention.instance_completed");
        assertEquals(0, events == null ? 0 : events);
    }

    @Test
    void realEventInsertFailureRollsBackCreation() {
        User user = userRepository.save(User.of("recurring-create-transaction@example.com", "not-used"));
        jdbcTemplate.execute("ALTER TABLE events ADD CONSTRAINT chk_events_reject_recurring_creation CHECK (type <> 'recurring_intention.created')");

        assertThrows(DataIntegrityViolationException.class, () -> recurringIntentionService.create(user.getId(),
                new CreateRecurringIntentionRequest(null, "Practice", 2, null, "flexible")));

        Integer intentions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recurring_intentions WHERE user_id = ?", Integer.class, user.getId());
        Integer events = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type = 'recurring_intention' AND type = ?", Integer.class, "recurring_intention.created");
        assertEquals(0, intentions == null ? 0 : intentions);
        assertEquals(0, events == null ? 0 : events);
    }
}
