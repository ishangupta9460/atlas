package com.atlas.backend.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class EventRepositoryAppendOnlyTest {
    @Test void applicationBoundaryDoesNotExposeGenericMutationMethods() {
        var names = Arrays.stream(EventRepository.class.getMethods()).map(Method::getName).toList();
        assertFalse(names.contains("save")); assertFalse(names.contains("saveAll"));
        assertFalse(names.contains("delete")); assertFalse(names.contains("deleteById")); assertFalse(names.contains("deleteAll"));
    }

    @Test void testCleanupCannotDeleteOutsideTheTestProfile() {
        EventJpaRepository events = mock(EventJpaRepository.class);
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("test")).thenReturn(false);
        EventRepository repository = new JpaEventRepository(events, environment);
        assertThrows(UnsupportedOperationException.class, repository::deleteAllForTest);
        verifyNoInteractions(events);
    }
}
