package com.atlas.backend.goal;

import com.atlas.backend.event.EventRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GoalStateMachineTest {
    @Mock private GoalRepository goalRepository;
    @Mock private EventRepository eventRepository;
    @InjectMocks private GoalService goalService;

    @Test
    void lifecycleAllowsOnlyActiveToCompletedOrAbandoned() {
        Goal completable = ownedGoal();
        when(goalRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(completable));
        goalService.complete(1L, 1L);
        assertEquals(Goal.COMPLETED, completable.getLifecycleState());
        assertEquals(Goal.ACTIVE, completable.getPlanningState());

        assertThrows(InvalidGoalStateException.class, () -> goalService.abandon(1L, 1L));

        Goal abandonable = ownedGoal();
        when(goalRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(abandonable));
        goalService.abandon(1L, 2L);
        assertEquals(Goal.ABANDONED, abandonable.getLifecycleState());
        assertEquals(Goal.ACTIVE, abandonable.getPlanningState());
        assertThrows(InvalidGoalStateException.class, () -> goalService.complete(1L, 2L));
    }

    @Test
    void planningTransitionsAllowExactlyTheDocumentedPathsAndPreserveLifecycle() {
        Goal goal = ownedGoal();
        when(goalRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(goal));

        goalService.defer(1L, 1L);
        assertEquals(Goal.DEFERRED, goal.getPlanningState());
        assertEquals(Goal.ACTIVE, goal.getLifecycleState());
        goalService.reactivate(1L, 1L);
        assertEquals(Goal.ACTIVE, goal.getPlanningState());

        goalService.markAtRisk(1L, 1L);
        assertEquals(Goal.AT_RISK, goal.getPlanningState());
        goalService.resolveRisk(1L, 1L);
        assertEquals(Goal.ACTIVE, goal.getPlanningState());

        goalService.markAtRisk(1L, 1L);
        goalService.pause(1L, 1L);
        assertEquals(Goal.PAUSED, goal.getPlanningState());
        goalService.resume(1L, 1L);
        assertEquals(Goal.ACTIVE, goal.getPlanningState());

        assertThrows(InvalidGoalStateException.class, () -> goalService.pause(1L, 1L));
        assertThrows(InvalidGoalStateException.class, () -> goalService.reactivate(1L, 1L));
        assertThrows(InvalidGoalStateException.class, () -> goalService.resume(1L, 1L));
    }

    @Test
    void deferredCanBeMarkedAtRiskButPausedCannot() {
        Goal deferred = ownedGoal();
        deferred.defer();
        when(goalRepository.findByIdAndUserId(eq(1L), eq(1L))).thenReturn(Optional.of(deferred));
        goalService.markAtRisk(1L, 1L);
        assertEquals(Goal.AT_RISK, deferred.getPlanningState());

        Goal paused = ownedGoal();
        paused.markAtRisk();
        paused.pause();
        when(goalRepository.findByIdAndUserId(eq(2L), eq(1L))).thenReturn(Optional.of(paused));
        assertThrows(InvalidGoalStateException.class, () -> goalService.markAtRisk(1L, 2L));
    }

    @Test
    void everyTransitionRecordsTheSpecifiedEventType() {
        Goal completable = ownedGoal();
        Goal abandonable = ownedGoal();
        Goal planning = ownedGoal();
        when(goalRepository.findByIdAndUserId(any(), any())).thenReturn(
                Optional.of(completable), Optional.of(abandonable),
                Optional.of(planning), Optional.of(planning), Optional.of(planning),
                Optional.of(planning), Optional.of(planning), Optional.of(planning), Optional.of(planning));

        goalService.complete(1L, 1L);
        goalService.abandon(1L, 2L);
        goalService.defer(1L, 3L);
        goalService.reactivate(1L, 3L);
        goalService.markAtRisk(1L, 3L);
        goalService.resolveRisk(1L, 3L);
        goalService.markAtRisk(1L, 3L);
        goalService.pause(1L, 3L);
        goalService.resume(1L, 3L);

        ArgumentCaptor<com.atlas.backend.event.Event> events = ArgumentCaptor.forClass(com.atlas.backend.event.Event.class);
        verify(eventRepository, times(9)).save(events.capture());
        assertEquals(java.util.List.of(
                "goal.completed", "goal.abandoned", "goal.deferred", "goal.reactivated",
                "goal.at_risk", "goal.risk_resolved", "goal.at_risk", "goal.paused", "goal.resumed"),
                events.getAllValues().stream().map(com.atlas.backend.event.Event::getType).toList());
        assertEquals("goal", events.getAllValues().get(0).getEntityType());
        assertEquals(java.util.List.of("user", "user", "atlas", "atlas", "atlas", "user", "atlas", "user", "user"),
                events.getAllValues().stream().map(com.atlas.backend.event.Event::getActor).toList());
        events.getAllValues().stream()
                .filter(event -> "atlas".equals(event.getActor()))
                .forEach(event -> assertFalse(event.getReason().isBlank()));
    }

    private Goal ownedGoal() { return Goal.create(1L, "Goal", null, null); }
}
