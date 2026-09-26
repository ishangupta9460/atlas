package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.dependency.CommitmentDependency;
import com.atlas.backend.dependency.DependencyLookahead;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** DEC-0013: bounded downstream count first, percentage completion second. */
public final class Stage5DependencyTier {
    private Stage5DependencyTier() {}

    public record Signal(int downstreamCount, BigDecimal completionPct,
                         boolean truncated, boolean cycleDetected) {}

    public static Signal evaluate(Commitment candidate, List<CommitmentDependency> edges,
                                  Map<Long, Commitment> ownedSnapshot) {
        var result = DependencyLookahead.downstream(candidate.getId(), edges);
        int count = 0;
        for (Long id : result.downstreamIds()) {
            Commitment downstream = Objects.requireNonNull(ownedSnapshot.get(id),
                    "Dependency endpoint missing from owned snapshot");
            if (!Objects.equals(candidate.getUserId(), downstream.getUserId()))
                throw new IllegalArgumentException("Dependency endpoints must share an owner");
            if (!"completed".equals(downstream.getWorkState()) && !"cancelled".equals(downstream.getWorkState()))
                count++;
        }
        return new Signal(count, candidate.getCurrentCompletionPct(), result.truncated(), result.cycleDetected());
    }

    /** Negative means first schedules before second, matching the existing tier comparators. */
    public static int compare(Signal first, Signal second) {
        int count = Integer.compare(second.downstreamCount(), first.downstreamCount());
        return count != 0 ? count : second.completionPct().compareTo(first.completionPct());
    }
}
