package com.atlas.backend.recurringintention;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.category.CategoryNotFoundException;
import com.atlas.backend.category.CategoryRepository;
import com.atlas.backend.goal.GoalNotFoundException;
import com.atlas.backend.goal.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns recurring-intention CRUD and its three documented weekly-cycle actions. */
@Service
public class RecurringIntentionService {
    private final RecurringIntentionRepository recurringIntentionRepository;
    private final GoalRepository goalRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    public RecurringIntentionService(RecurringIntentionRepository recurringIntentionRepository,
                                     GoalRepository goalRepository, CategoryRepository categoryRepository, EventRepository eventRepository) {
        this.recurringIntentionRepository = recurringIntentionRepository;
        this.goalRepository = goalRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public RecurringIntentionResponse create(Long userId, CreateRecurringIntentionRequest request) {
        validateOwnedGoal(userId, request.goalId());
        validateOwnedCategory(userId, request.categoryId());
        RecurringIntention intention = recurringIntentionRepository.save(RecurringIntention.create(userId,
                request.goalId(), request.title(), request.targetCountPerWeek(), request.categoryId(), request.flexibilityTier()));
        writeEvent(intention, "recurring_intention.created", "user", null);
        return RecurringIntentionResponse.from(intention);
    }

    @Transactional(readOnly = true)
    public RecurringIntentionResponse get(Long userId, Long id) { return RecurringIntentionResponse.from(findOwned(userId, id)); }

    /** A changed target deliberately applies at the next weekly reset, not retroactively this week. */
    @Transactional
    public RecurringIntentionResponse updateTarget(Long userId, Long id, UpdateRecurringIntentionTargetRequest request) {
        RecurringIntention intention = findOwned(userId, id);
        intention.updateTarget(request.targetCountPerWeek());
        writeEvent(intention, "recurring_intention.target_updated", "user", null);
        return RecurringIntentionResponse.from(intention);
    }

    @Transactional
    public RecurringIntentionResponse completeInstance(Long userId, Long id) {
        RecurringIntention intention = findOwned(userId, id);
        intention.completeInstance();
        writeEvent(intention, "recurring_intention.instance_completed", "user", null);
        return RecurringIntentionResponse.from(intention);
    }

    @Transactional
    public RecurringIntentionResponse reportMissedInstance(Long userId, Long id) {
        RecurringIntention intention = findOwned(userId, id);
        writeEvent(intention, "recurring_intention.instance_missed", "user", null);
        return RecurringIntentionResponse.from(intention);
    }

    /** Internal operational hook; no scheduler or public route exists yet. */
    @Transactional
    public RecurringIntentionResponse resetForNewWeek(Long userId, Long id) {
        RecurringIntention intention = findOwned(userId, id);
        intention.resetForNewWeek();
        writeEvent(intention, "recurring_intention.reset", "atlas", "A new week began; the recurring weekly target was reset in full without carrying prior-week backlog.");
        return RecurringIntentionResponse.from(intention);
    }

    private RecurringIntention findOwned(Long userId, Long id) {
        return recurringIntentionRepository.findByIdAndUserId(id, userId).orElseThrow(RecurringIntentionNotFoundException::new);
    }
    private void validateOwnedGoal(Long userId, Long goalId) {
        if (goalId != null && !goalRepository.existsByIdAndUserId(goalId, userId)) throw new GoalNotFoundException();
    }
    private void validateOwnedCategory(Long userId, Long categoryId) {
        if (categoryId != null && !categoryRepository.existsByIdAndUserId(categoryId, userId)) throw new CategoryNotFoundException();
    }
    private void writeEvent(RecurringIntention intention, String type, String actor, String reason) {
        recurringIntentionRepository.flush();
        eventRepository.append(Event.forEntity("recurring_intention", intention.getId(), type, actor, reason));
    }
}
