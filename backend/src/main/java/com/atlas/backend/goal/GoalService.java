package com.atlas.backend.goal;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns Goal CRUD and both independent Goal state machines. */
@Service
public class GoalService {
    private final GoalRepository goalRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper payloadMapper = new ObjectMapper();

    public GoalService(GoalRepository goalRepository, EventRepository eventRepository) {
        this.goalRepository = goalRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public GoalResponse create(Long userId, CreateGoalRequest request) {
        Goal goal = goalRepository.save(Goal.create(
                userId, request.title(), request.description(), request.targetDeadline()));
        writeCrudEvent(goal, "goal.created", Map.of("after", snapshot(goal)));
        return GoalResponse.from(goal);
    }

    @Transactional(readOnly = true)
    public GoalResponse get(Long userId, Long goalId) { return GoalResponse.from(findOwnedGoal(userId, goalId)); }

    public record GoalPage(java.util.List<GoalResponse> goals, Long nextCursor) {}

    @Transactional(readOnly = true)
    public GoalPage list(Long userId, Long cursor, int limit) {
        var rows = goalRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId,
                cursor == null ? Long.MAX_VALUE : cursor,
                org.springframework.data.domain.PageRequest.of(0, limit + 1));
        var goals = rows.stream().limit(limit).map(GoalResponse::from).toList();
        return new GoalPage(goals, rows.size() > limit ? goals.get(goals.size() - 1).id() : null);
    }

    @Transactional
    public GoalResponse update(Long userId, Long goalId, UpdateGoalRequest request) {
        Goal goal = findOwnedGoal(userId, goalId);
        Map<String, Object> before = snapshot(goal);
        goal.update(request.getTitle(), request.getTargetDeadline(), request.isTargetDeadlineProvided());
        Map<String, Object> after = snapshot(goal);
        if (!before.equals(after)) writeCrudEvent(goal, "goal.updated", Map.of("before", before, "after", after));
        return GoalResponse.from(goal);
    }

    /** Explicit user completion; no automatic roadmap trigger exists until Roadmap is implemented. */
    @Transactional
    public GoalResponse complete(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requireLifecycle(goal, Goal.ACTIVE, "Only active goals can be completed");
        goal.complete();
        writeUserEvent(goal, "goal.completed");
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse abandon(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requireLifecycle(goal, Goal.ACTIVE, "Only active goals can be abandoned");
        goal.abandon();
        writeUserEvent(goal, "goal.abandoned");
        return GoalResponse.from(goal);
    }

    /** Internal scheduling hook; deliberately not exposed as an API endpoint in DOM-001. */
    @Transactional
    public GoalResponse defer(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requirePlanning(goal, Goal.ACTIVE, "Only active goals can be deferred");
        goal.defer();
        writeAtlasEvent(goal, "goal.deferred",
                "Scheduling overload pushed this goal's work out of the current week.");
        return GoalResponse.from(goal);
    }

    /** Internal scheduling hook; deliberately not exposed as an API endpoint in DOM-001. */
    @Transactional
    public GoalResponse reactivate(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requirePlanning(goal, Goal.DEFERRED, "Only deferred goals can be reactivated");
        goal.reactivate();
        writeAtlasEvent(goal, "goal.reactivated",
                "Capacity became available and this goal re-entered the scheduling candidate pool.");
        return GoalResponse.from(goal);
    }

    /** Internal Goal Risk hook; deliberately not exposed until the risk calculator exists. */
    @Transactional
    public GoalResponse markAtRisk(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        if (!Goal.ACTIVE.equals(goal.getPlanningState()) && !Goal.DEFERRED.equals(goal.getPlanningState())) {
            throw new InvalidGoalStateException("Only active or deferred goals can be marked at risk");
        }
        goal.markAtRisk();
        writeAtlasEvent(goal, "goal.at_risk",
                "Goal feasibility calculation found insufficient available capacity for the remaining work before its deadline.");
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse resolveRisk(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requirePlanning(goal, Goal.AT_RISK, "Only at-risk goals can have risk resolved");
        goal.resolveRisk();
        writeUserEvent(goal, "goal.risk_resolved");
        return GoalResponse.from(goal);
    }

    @Transactional
    public GoalResponse pause(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requirePlanning(goal, Goal.AT_RISK, "Only at-risk goals can be paused");
        goal.pause();
        writeUserEvent(goal, "goal.paused");
        return GoalResponse.from(goal);
    }

    /** Explicit user resume transition; no DOM-001 API route was specified for it. */
    @Transactional
    public GoalResponse resume(Long userId, Long goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        requirePlanning(goal, Goal.PAUSED, "Only paused goals can be resumed");
        goal.resume();
        writeUserEvent(goal, "goal.resumed");
        return GoalResponse.from(goal);
    }

    private Goal findOwnedGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(GoalNotFoundException::new);
    }

    private Map<String, Object> snapshot(Goal goal) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", goal.getId());
        snapshot.put("userId", goal.getUserId());
        snapshot.put("title", goal.getTitle());
        snapshot.put("description", goal.getDescription());
        snapshot.put("targetDeadline", goal.getTargetDeadline() == null ? null : goal.getTargetDeadline().toString());
        snapshot.put("lifecycleState", goal.getLifecycleState());
        snapshot.put("planningState", goal.getPlanningState());
        return snapshot;
    }

    private void writeCrudEvent(Goal goal, String type, Map<String, Object> payload) {
        flushGoalStateBeforeEvent();
        eventRepository.append(Event.forEntity("goal", goal.getId(), type, "user", null,
                payloadMapper.writeValueAsString(payload)));
    }

    private void requireLifecycle(Goal goal, String expected, String message) {
        if (!expected.equals(goal.getLifecycleState())) throw new InvalidGoalStateException(message);
    }

    private void requirePlanning(Goal goal, String expected, String message) {
        if (!expected.equals(goal.getPlanningState())) throw new InvalidGoalStateException(message);
    }

    private void writeUserEvent(Goal goal, String type) {
        flushGoalStateBeforeEvent();
        eventRepository.append(Event.forEntity("goal", goal.getId(), type, "user"));
    }

    private void writeAtlasEvent(Goal goal, String type, String reason) {
        flushGoalStateBeforeEvent();
        eventRepository.append(Event.forEntity("goal", goal.getId(), type, "atlas", reason));
    }

    /**
     * Issue the Goal update before its paired Event insert. Both statements remain
     * in the enclosing transaction, so an Event insert failure rolls the update
     * back while making the write ordering explicit and testable.
     */
    private void flushGoalStateBeforeEvent() {
        goalRepository.flush();
    }
}
