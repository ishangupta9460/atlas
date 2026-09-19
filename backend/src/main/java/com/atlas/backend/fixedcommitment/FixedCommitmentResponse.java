package com.atlas.backend.fixedcommitment;

import java.time.Instant;

public record FixedCommitmentResponse(Long id, String title, Instant startTime, Instant endTime,
                                      String source, String recurrenceRule, String flexibilityTier) {
    static FixedCommitmentResponse from(FixedCommitment value) {
        return new FixedCommitmentResponse(value.getId(), value.getTitle(), value.getStartTime(), value.getEndTime(),
                value.getSource(), value.getRecurrenceRule(), value.getFlexibilityTier());
    }
}
