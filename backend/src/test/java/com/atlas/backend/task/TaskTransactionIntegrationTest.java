package com.atlas.backend.task;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Verifies a failed paired event write rolls back the enclosing task transition. */
@SpringBootTest
@ActiveProfiles("test")
class TaskTransactionIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private EventRepository eventRepository;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void failedEventWriteRollsBackTaskStateTransition() {
        User user = userRepository.save(User.of("transaction@example.com", "not-used-in-this-test"));
        Task task = taskRepository.save(Task.ready(user.getId(), "Transactional task"));
        doThrow(new IllegalStateException("event store unavailable"))
                .when(eventRepository).save(any());

        assertThrows(IllegalStateException.class, () -> taskService.start(user.getId(), task.getId()));

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(Task.READY, reloaded.getStatus());
        assertEquals(null, reloaded.getStartedAt());
    }
}
