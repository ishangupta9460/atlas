package com.atlas.backend.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class Stage7HistoricalCalibrationTest {

    @Test
    void missingHistoryIsExplicitAndDoesNotUseCapacityAsAProbability() {
        var result = Stage7HistoricalCalibration.missingHistory();

        assertFalse(result.hasHistoricalEvidence());
        assertTrue(result.historicalCompletionProbability().isEmpty());
    }

    @Test
    void historicalProbabilityIsRetainedForCalibration() {
        var result = Stage7HistoricalCalibration.fromHistoricalCompletionProbability(new BigDecimal("0.58"));

        assertTrue(result.hasHistoricalEvidence());
        assertEquals(new BigDecimal("0.58"), result.historicalCompletionProbability().orElseThrow());
    }

    @Test
    void repeatedEvaluationIsDeterministic() {
        for (int index = 0; index < 20; index++) {
            assertEquals(Stage7HistoricalCalibration.missingHistory(), Stage7HistoricalCalibration.missingHistory());
            assertEquals(
                Stage7HistoricalCalibration.fromHistoricalCompletionProbability(new BigDecimal("0.92")),
                Stage7HistoricalCalibration.fromHistoricalCompletionProbability(new BigDecimal("0.92")));
        }
    }

    @Test
    void stageSevenDoesNotProvideCandidateRanking() {
        assertFalse(Comparator.class.isAssignableFrom(Stage7HistoricalCalibration.class));
    }

    @Test
    void invalidHistoricalProbabilitiesAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> Stage7HistoricalCalibration.fromHistoricalCompletionProbability(new BigDecimal("1.01")));
        assertThrows(IllegalArgumentException.class,
            () -> Stage7HistoricalCalibration.fromHistoricalCompletionProbability(new BigDecimal("-0.01")));
    }
}
