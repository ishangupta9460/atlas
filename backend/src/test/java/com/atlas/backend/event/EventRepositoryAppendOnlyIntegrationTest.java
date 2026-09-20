package com.atlas.backend.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EventRepositoryAppendOnlyIntegrationTest {
    @Autowired private EventRepository events;
    @AfterEach void cleanUp() { events.deleteAllForTest(); }

    @Test void appendsAndPreservesPersistedEventsWhileTheBoundaryRemainsReadOnly() {
        Event appended = events.append(Event.forEntity("commitment", 99L, "task.created", "user"));
        Event persisted = events.findById(appended.getId()).orElseThrow();
        assertEquals("task.created", persisted.getType());
        assertEquals("commitment", persisted.getEntityType());
        assertEquals(99L, persisted.getEntityId());
        assertFalse(Arrays.stream(EventRepository.class.getMethods()).map(Method::getName)
            .anyMatch(name -> name.equals("save") || name.equals("delete") || name.equals("deleteById") || name.equals("deleteAll")));
        assertEquals("task.created", events.findById(appended.getId()).orElseThrow().getType());
    }
}
