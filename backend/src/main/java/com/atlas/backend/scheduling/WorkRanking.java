package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import java.time.Instant;
import java.util.*;

/** One lexicographic decision path shared by ordering and explanations. Never a composite score. */
public final class WorkRanking {
    public record Candidate(Commitment work, Stage5DependencyTier.Signal dependencies, boolean atRisk) {}
    public record Decision(int order, String reason, boolean importantTie) {}
    private final Long precedingCategory;
    public WorkRanking(Long precedingCategory) { this.precedingCategory = precedingCategory; }

    public Decision compare(Candidate a, Candidate b) {
        var earlier = compareThroughStage6(a, b);
        if (earlier.order() != 0) return earlier;
        int order = Stage8CategoryContinuity.compare(a.work(), b.work(), precedingCategory);
        if (order != 0) return decision(order, "This preserves category continuity in the first shared available window.");
        return tieBreak(a, b);
    }

    public Decision compareThroughStage6(Candidate a, Candidate b) {
        var x = a.work(); var y = b.work();
        int order = Boolean.compare(Stage2HardConsequenceGate.qualifies(y), Stage2HardConsequenceGate.qualifies(x));
        if (order != 0) return decision(order, "The confirmed hard consequence takes priority.");
        order = Stage3UserImportanceTier.instance().compare(x, y);
        if (order != 0) return decision(order, "Your assigned importance takes priority.");
        order = -Stage4GoalAtRiskTier.compareStatuses(status(a), status(b));
        if (order != 0) return decision(order, "This work protects a goal that is at risk.");
        order = Stage5DependencyTier.compare(a.dependencies(), b.dependencies());
        if (order != 0) return decision(order, a.dependencies().downstreamCount() != b.dependencies().downstreamCount()
                ? "This work unlocks more unfinished dependent work." : "This work is closer to completion.");
        order = Stage6FlexibilityTier.compare(x, y);
        if (order != 0) return decision(order, "Deferring the other work is less disruptive given its flexibility.");
        // Stage 7 has no ranking input, including when historical evidence exists.
        return decision(0, "Equivalent through flexibility.");
    }

    public static Decision tieBreak(Candidate a, Candidate b) {
        var x = a.work(); var y = b.work();
        int order = y.getCurrentCompletionPct().compareTo(x.getCurrentCompletionPct());
        if (order != 0) return decision(order, "This work is closer to completion.");
        order = Comparator.nullsLast(Comparator.<Instant>naturalOrder()).compare(x.getOwnDeadline(), y.getOwnDeadline());
        if (order != 0) return decision(order, "This work has the nearer deadline.");
        order = Stage6FlexibilityTier.compare(x, y);
        if (order != 0) return decision(order, "Deferring the other work is less disruptive given its flexibility.");
        order = Integer.compare(b.dependencies().downstreamCount(), a.dependencies().downstreamCount());
        if (order != 0) return decision(order, "This work has the stronger unfinished dependency chain.");
        order = x.getCreatedAt().compareTo(y.getCreatedAt());
        if (order == 0) order = x.getId().compareTo(y.getId());
        return new Decision(order, "Otherwise equally ranked work is ordered by creation time.", important(a) && important(b));
    }

    private static Stage4GoalAtRiskTier.Status status(Candidate c) {
        return Stage4GoalAtRiskTier.evaluateStatus(c.work().getGoalId(), id -> c.atRisk());
    }
    private static boolean important(Candidate c) {
        return ImportanceTier.from(c.work().getImportance()).getRank() >= ImportanceTier.HIGH.getRank()
                || status(c) == Stage4GoalAtRiskTier.Status.BOOSTED;
    }
    private static Decision decision(int order, String reason) { return new Decision(order, reason, false); }
}
