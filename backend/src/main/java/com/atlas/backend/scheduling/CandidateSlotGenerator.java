package com.atlas.backend.scheduling;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Returns fitting ranges, not ranked placements. Every start in [earliestStart, latestStart] fits. */
@Component
public class CandidateSlotGenerator {
    public record CandidateSlot(Instant earliestStart, Instant latestStart, Instant availableEnd,
                                long workSeconds, long elapsedSeconds) {}

    public List<CandidateSlot> generate(List<TimeInterval> working, List<TimeInterval> unavailable,
                                        TimeInterval planningWindow, long workSeconds,
                                        CapacityCalculator.Policy policy) {
        long elapsed = new CapacityCalculator().elapsedSeconds(workSeconds, policy);
        return TimeInterval.subtract(TimeInterval.clip(working, planningWindow), unavailable).stream()
                .filter(i -> i.seconds() >= elapsed)
                .map(i -> new CandidateSlot(i.start(), i.end().minusSeconds(elapsed), i.end(), workSeconds, elapsed))
                .toList();
    }
}
