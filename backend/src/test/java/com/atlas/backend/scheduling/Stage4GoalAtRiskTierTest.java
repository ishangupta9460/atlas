package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.goal.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class Stage4GoalAtRiskTierTest {

    private Commitment createCommitmentWithGoal(Long id, Long goalId) {
        try {
            var constructor = Commitment.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Commitment c = constructor.newInstance();
            ReflectionTestUtils.setField(c, "id", id);
            ReflectionTestUtils.setField(c, "userId", 1L);
            ReflectionTestUtils.setField(c, "goalId", goalId);
            ReflectionTestUtils.setField(c, "title", "Task " + id);
            ReflectionTestUtils.setField(c, "completionCriterion", "Criterion");
            ReflectionTestUtils.setField(c, "importance", "medium");
            ReflectionTestUtils.setField(c, "flexibilityTier", "flexible");
            ReflectionTestUtils.setField(c, "workState", "ready");
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Goal createGoalWithPlanningState(Long id, String planningState) {
        try {
            var constructor = Goal.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Goal g = constructor.newInstance();
            ReflectionTestUtils.setField(g, "id", id);
            ReflectionTestUtils.setField(g, "userId", 1L);
            ReflectionTestUtils.setField(g, "planningState", planningState);
            ReflectionTestUtils.setField(g, "lifecycleState", Goal.ACTIVE);
            return g;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Evaluate status directly from Goal entity")
    void testEvaluateStatusFromGoal() {
        Goal atRiskGoal = createGoalWithPlanningState(1L, Goal.AT_RISK);
        Goal activeGoal = createGoalWithPlanningState(2L, Goal.ACTIVE);
        Goal deferredGoal = createGoalWithPlanningState(3L, Goal.DEFERRED);
        Goal nullStateGoal = createGoalWithPlanningState(4L, null);

        assertEquals(Stage4GoalAtRiskTier.Status.BOOSTED, Stage4GoalAtRiskTier.evaluateStatus(atRiskGoal));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus(activeGoal));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus(deferredGoal));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus(nullStateGoal));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus((Goal) null));
    }

    @Test
    @DisplayName("Evaluate status with Predicate lookup")
    void testEvaluateStatusWithPredicate() {
        Set<Long> atRiskGoalIds = Set.of(100L, 200L);
        Predicate<Long> riskPredicate = atRiskGoalIds::contains;

        Commitment cAtRisk = createCommitmentWithGoal(1L, 100L);
        Commitment cNormal = createCommitmentWithGoal(2L, 300L);
        Commitment cUnlinked = createCommitmentWithGoal(3L, null);

        assertEquals(Stage4GoalAtRiskTier.Status.BOOSTED, Stage4GoalAtRiskTier.evaluateStatus(cAtRisk, riskPredicate));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus(cNormal, riskPredicate));
        assertEquals(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.evaluateStatus(cUnlinked, riskPredicate));
    }

    @Test
    @DisplayName("Require non-null predicate and commitment")
    void testNullGuards() {
        Commitment c = createCommitmentWithGoal(1L, 100L);
        Predicate<Long> predicate = goalId -> true;

        assertThrows(NullPointerException.class, () -> Stage4GoalAtRiskTier.evaluateStatus(100L, null));
        assertThrows(NullPointerException.class, () -> Stage4GoalAtRiskTier.evaluateStatus(c, null));
        assertThrows(NullPointerException.class, () -> Stage4GoalAtRiskTier.evaluateStatus((Commitment) null, predicate));
        assertThrows(NullPointerException.class, () -> new Stage4GoalAtRiskTier(null));
    }

    @Test
    @DisplayName("Stage 4 status comparison ordering: BOOSTED > NORMAL")
    void testStatusComparison() {
        assertTrue(Stage4GoalAtRiskTier.compareStatuses(Stage4GoalAtRiskTier.Status.BOOSTED, Stage4GoalAtRiskTier.Status.NORMAL) > 0);
        assertTrue(Stage4GoalAtRiskTier.compareStatuses(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.Status.BOOSTED) < 0);
        assertEquals(0, Stage4GoalAtRiskTier.compareStatuses(Stage4GoalAtRiskTier.Status.BOOSTED, Stage4GoalAtRiskTier.Status.BOOSTED));
        assertEquals(0, Stage4GoalAtRiskTier.compareStatuses(Stage4GoalAtRiskTier.Status.NORMAL, Stage4GoalAtRiskTier.Status.NORMAL));
    }

    @Test
    @DisplayName("Commitment comparison using risk predicate")
    void testCommitmentComparison() {
        Predicate<Long> riskPredicate = goalId -> Long.valueOf(10L).equals(goalId);

        Commitment c1 = createCommitmentWithGoal(1L, 10L); // at risk
        Commitment c2 = createCommitmentWithGoal(2L, 20L); // not at risk

        assertTrue(Stage4GoalAtRiskTier.compareCommitments(c1, c2, riskPredicate) > 0);
        assertTrue(Stage4GoalAtRiskTier.compareCommitments(c2, c1, riskPredicate) < 0);
        assertEquals(0, Stage4GoalAtRiskTier.compareCommitments(c1, c1, riskPredicate));
    }

    @Test
    @DisplayName("Deterministic evaluation")
    void testDeterminism() {
        Predicate<Long> riskPredicate = goalId -> Long.valueOf(50L).equals(goalId);
        Commitment c1 = createCommitmentWithGoal(1L, 50L);
        Commitment c2 = createCommitmentWithGoal(2L, 99L);

        int run1 = Stage4GoalAtRiskTier.compareCommitments(c1, c2, riskPredicate);
        int run2 = Stage4GoalAtRiskTier.compareCommitments(c1, c2, riskPredicate);
        int run3 = Stage4GoalAtRiskTier.compareCommitments(c1, c2, riskPredicate);

        assertEquals(run1, run2);
        assertEquals(run2, run3);
        assertTrue(run1 > 0);
    }

    @Test
    @DisplayName("Collection sorting using Stage4GoalAtRiskTier comparator instance")
    void testCollectionSorting() {
        Predicate<Long> riskPredicate = goalId -> Long.valueOf(100L).equals(goalId);
        Stage4GoalAtRiskTier comparator = new Stage4GoalAtRiskTier(riskPredicate);

        Commitment normal1 = createCommitmentWithGoal(1L, 10L);
        Commitment boosted = createCommitmentWithGoal(2L, 100L);
        Commitment normal2 = createCommitmentWithGoal(3L, null);

        List<Commitment> list = new ArrayList<>(List.of(normal1, boosted, normal2));
        list.sort(comparator);

        assertEquals(2L, list.get(0).getId()); // Boosted task comes first
        assertEquals(Stage4GoalAtRiskTier.Status.BOOSTED, Stage4GoalAtRiskTier.evaluateStatus(list.get(0), riskPredicate));
    }
}
