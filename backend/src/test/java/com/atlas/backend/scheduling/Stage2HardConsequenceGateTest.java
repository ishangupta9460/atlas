package com.atlas.backend.scheduling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atlas.backend.commitment.Commitment;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class Stage2HardConsequenceGateTest {

    private Commitment commitment(boolean hardConsequence, Instant deadline) {
        try {
            var constructor = Commitment.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Commitment commitment = constructor.newInstance();
            ReflectionTestUtils.setField(commitment, "hardConsequence", hardConsequence);
            ReflectionTestUtils.setField(commitment, "ownDeadline", deadline);
            return commitment;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void deadlineAloneDoesNotQualify() {
        assertFalse(Stage2HardConsequenceGate.qualifies(
            commitment(false, Instant.parse("2026-10-01T12:00:00Z"))));
    }

    @Test
    void validatedHardConsequenceQualifies() {
        assertTrue(Stage2HardConsequenceGate.qualifies(
            commitment(true, Instant.parse("2026-10-01T12:00:00Z"))));
    }

    @Test
    void unresolvedClassificationUsesTheFalseDefault() {
        assertFalse(Stage2HardConsequenceGate.qualifies(commitment(false, null)));
    }

    @Test
    void repeatedEvaluationIsDeterministic() {
        Commitment commitment = commitment(true, Instant.parse("2026-10-01T12:00:00Z"));
        for (int index = 0; index < 20; index++) {
            assertTrue(Stage2HardConsequenceGate.qualifies(commitment));
        }
    }

    @Test
    void nullCommitmentIsRejected() {
        assertThrows(NullPointerException.class, () -> Stage2HardConsequenceGate.qualifies(null));
    }
}
