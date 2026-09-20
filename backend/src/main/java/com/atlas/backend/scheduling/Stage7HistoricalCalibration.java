package com.atlas.backend.scheduling;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage 7 historical-execution calibration seam.
 *
 * <p>Stage 7 deliberately does not compare candidates. It carries a validated
 * historical completion probability when Analytics supplies one, or an explicit
 * missing-history result while Analytics is unavailable.
 */
public final class Stage7HistoricalCalibration {

    private Stage7HistoricalCalibration() {
    }

    public record Result(Optional<BigDecimal> historicalCompletionProbability) {
        public Result {
            Objects.requireNonNull(historicalCompletionProbability,
                "historicalCompletionProbability must not be null");
            historicalCompletionProbability.ifPresent(value -> {
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                    throw new IllegalArgumentException("historical completion probability must be between 0 and 1");
                }
            });
        }

        public boolean hasHistoricalEvidence() {
            return historicalCompletionProbability.isPresent();
        }
    }

    public static Result missingHistory() {
        return new Result(Optional.empty());
    }

    public static Result fromHistoricalCompletionProbability(BigDecimal probability) {
        return new Result(Optional.of(Objects.requireNonNull(probability,
            "historical completion probability must not be null")));
    }
}
