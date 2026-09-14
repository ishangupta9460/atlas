package com.atlas.backend.fixedcommitment;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixedCommitmentTest {
    private final Instant start = Instant.parse("2026-09-14T10:00:00.123456789Z");
    private final Instant end = Instant.parse("2026-09-14T11:00:00Z");

    @Test void fixedSourceAndMicrosecondInvariants() {
        for (String source : new String[]{"manual", "screenshot_import"}) {
            FixedCommitment value = FixedCommitment.create(1L, "Reservation", start, end, source);
            assertEquals("fixed", value.getFlexibilityTier());
            assertEquals(source, value.getSource());
            assertNull(value.getRecurrenceRule());
            assertEquals(Instant.parse("2026-09-14T10:00:00.123456Z"), value.getStartTime());
        }
        assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitment.create(1L, "Valid", start, end, "ai"));
        assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitment.create(null, "Valid", start, end, "manual"));
    }

    @Test void rejectsInvalidIntervalsTitlesAndDatabaseRange() {
        for (Instant invalidEnd : new Instant[]{null, start, start.minusSeconds(1), start.plusNanos(1)}) {
            assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitment.create(1L, "Valid", start, invalidEnd, "manual"));
        }
        for (String title : new String[]{null, "", " \t\n", "x".repeat(256)}) {
            assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitment.create(1L, title, start, end, "manual"));
        }
        assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitment.create(1L, "Valid", Instant.MIN, end, "manual"));
        FixedCommitment value = FixedCommitment.create(1L, "Valid", start, end, "manual");
        assertThrows(InvalidFixedCommitmentException.class, () -> value.update("Changed", end, start, false));
        assertEquals("Valid", value.getTitle());
    }

    @Test void strictTypesOffsetsAndRecurrence() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(Instant.parse("2026-09-14T10:00:00Z"), FixedCommitmentInput.time(mapper.readTree("\"2026-09-14T15:30:00+05:30\""), "startTime"));
        for (String json : new String[]{"null", "12", "true", "{}", "\"2026-09-14T10:00:00\""}) {
            assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitmentInput.time(mapper.readTree(json), "startTime"));
        }
        FixedCommitmentInput.recurrence(null);
        FixedCommitmentInput.recurrence(mapper.nullNode());
        for (String json : new String[]{"\"\"", "\"FREQ=DAILY\"", "{}", "[]", "1"}) {
            assertThrows(InvalidFixedCommitmentException.class, () -> FixedCommitmentInput.recurrence(mapper.readTree(json)));
        }
    }
}
