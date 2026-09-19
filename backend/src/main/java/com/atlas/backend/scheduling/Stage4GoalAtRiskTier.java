package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.goal.Goal;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Implements Stage 4 (Goal Importance & At-Risk Status) of the Atlas Scheduling Engine
 * as specified in 04_SCHEDULING_ENGINE.md §2.
 *
 * <p>Stage 4 performs pure tier evaluation and comparison for candidate items linked
 * to Goals in an 'at_risk' Planning State.
 *
 * <p>Rule: Tasks linked to a Goal in at_risk Planning State (02 §2.2) receive a protective boost
 * (BOOSTED > NORMAL). Stage 4 never outranks Stage 0-2.
 */
public final class Stage4GoalAtRiskTier implements Comparator<Commitment> {

    public enum Status {
        NORMAL,
        BOOSTED
    }

    private final Predicate<Long> isGoalAtRiskPredicate;

    public Stage4GoalAtRiskTier(Predicate<Long> isGoalAtRiskPredicate) {
        this.isGoalAtRiskPredicate = Objects.requireNonNull(isGoalAtRiskPredicate, "isGoalAtRiskPredicate must not be null");
    }

    public static Status evaluateStatus(Long goalId, Predicate<Long> isGoalAtRiskPredicate) {
        Objects.requireNonNull(isGoalAtRiskPredicate, "isGoalAtRiskPredicate must not be null");
        if (goalId == null) {
            return Status.NORMAL;
        }
        return isGoalAtRiskPredicate.test(goalId) ? Status.BOOSTED : Status.NORMAL;
    }

    public static Status evaluateStatus(Commitment commitment, Predicate<Long> isGoalAtRiskPredicate) {
        Objects.requireNonNull(commitment, "commitment must not be null");
        Objects.requireNonNull(isGoalAtRiskPredicate, "isGoalAtRiskPredicate must not be null");
        return evaluateStatus(commitment.getGoalId(), isGoalAtRiskPredicate);
    }

    public static Status evaluateStatus(Goal goal) {
        if (goal == null || goal.getPlanningState() == null) {
            return Status.NORMAL;
        }
        return Goal.AT_RISK.equalsIgnoreCase(goal.getPlanningState()) ? Status.BOOSTED : Status.NORMAL;
    }

    public static int compareStatuses(Status first, Status second) {
        Objects.requireNonNull(first, "first status must not be null");
        Objects.requireNonNull(second, "second status must not be null");
        if (first == second) {
            return 0;
        }
        return first == Status.BOOSTED ? 1 : -1;
    }

    public static int compareCommitments(Commitment first, Commitment second, Predicate<Long> isGoalAtRiskPredicate) {
        Status s1 = evaluateStatus(first, isGoalAtRiskPredicate);
        Status s2 = evaluateStatus(second, isGoalAtRiskPredicate);
        return compareStatuses(s1, s2);
    }

    @Override
    public int compare(Commitment c1, Commitment c2) {
        // Reverse standard rank order for collection sorting (higher status/BOOSTED comes first)
        return compareCommitments(c2, c1, isGoalAtRiskPredicate);
    }
}
