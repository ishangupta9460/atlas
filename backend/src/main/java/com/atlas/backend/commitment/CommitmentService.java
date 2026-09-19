package com.atlas.backend.commitment;

import com.atlas.backend.category.Category;
import com.atlas.backend.category.CategoryRepository;
import com.atlas.backend.goal.GoalRepository;
import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.atlas.backend.commitment.CommitmentRequest.*;

@Service
public class CommitmentService {
    private final CommitmentRepository repository;
    private final CategoryRepository categories;
    private final GoalRepository goals;
    private final EventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();
    public CommitmentService(CommitmentRepository repository, CategoryRepository categories, GoalRepository goals, EventRepository events) {
        this.repository=repository; this.categories=categories; this.goals=goals; this.events=events;
    }
    @Transactional public CommitmentResponse create(Long owner, CommitmentRequest request) {
        Commitment value=Commitment.create(owner, fields(owner,null,request));
        repository.saveAndFlush(value);
        event(value,"task.created",Map.of("after",snapshot(value)));
        ready(value);
        return CommitmentResponse.from(value);
    }
    @Transactional(readOnly=true) public CommitmentResponse get(Long owner, Long id) {
        return CommitmentResponse.from(repository.findByIdAndUserId(id,owner).orElseThrow(CommitmentException::missing));
    }
    @Transactional public CommitmentResponse update(Long owner, Long id, CommitmentRequest request) {
        Commitment value=repository.lockOwned(id,owner).orElseThrow(CommitmentException::missing);
        Map<String,Object> before=snapshot(value);
        value.update(fields(owner,value,request));
        Map<String,Object> after=snapshot(value);
        if (!before.equals(after)) event(value,"task.updated",Map.of("before",before,"after",after));
        ready(value);
        return CommitmentResponse.from(value);
    }
    /** Internal execution integration seam; deliberately not exposed as an HTTP state setter. */
    @Transactional
    CommitmentResponse transition(Long owner, Long id, String target, boolean blockPlaced, boolean userTrigger) {
        Commitment value=repository.lockOwned(id,owner).orElseThrow(CommitmentException::missing);
        String previous=value.getWorkState();
        Map<String,Object> before=snapshot(value);
        value.transition(target,blockPlaced,userTrigger);
        String type = switch(target) {
            case "in_progress" -> "task.started";
            case "completed" -> "task.completed";
            case "ready" -> "draft".equals(previous) ? "task.ready" : "task.partial";
            default -> throw CommitmentException.state();
        };
        event(value,type,Map.of("before",before,"after",snapshot(value)));
        return CommitmentResponse.from(value);
    }
    private void ready(Commitment value) {
        Map<String,Object> before=snapshot(value);
        if (value.establishReadiness()) event(value,"task.ready",Map.of("before",before,"after",snapshot(value)));
    }
    private Commitment.Fields fields(Long owner, Commitment old, CommitmentRequest r) {
        Long milestone = r.milestoneId()==null && old!=null ? old.getMilestoneId() : id(r.milestoneId());
        Long goal = r.goalId()==null && old!=null ? old.getGoalId() : id(r.goalId());
        if (milestone!=null) {
            Long derived=repository.ownedMilestoneGoal(milestone,owner).orElseThrow(CommitmentException::missing);
            // A newly selected Milestone derives its Goal unless an explicit Goal was supplied.
            if (r.goalId()!=null && !derived.equals(goal)) {
                if (goal!=null && !goals.existsByIdAndUserId(goal,owner)) throw CommitmentException.missing();
                throw CommitmentException.invalid("goalId must match the Milestone's Goal");
            }
            goal=derived;
        }
        if (goal!=null && !goals.existsByIdAndUserId(goal,owner)) throw CommitmentException.missing();
        Long categoryId=r.categoryId()==null && old!=null ? old.getCategoryId() : id(r.categoryId());
        Category category=categoryId==null ? null : categories.lockOwned(categoryId,owner).orElseThrow(CommitmentException::missing);
        String importance=r.importance()==null && old!=null ? old.getImportance() : string(r.importance());
        String flexibility=r.flexibilityTier()==null && old!=null ? old.getFlexibilityTier() : string(r.flexibilityTier());
        // Explicit null is not an enum value; omission alone requests a creation default.
        if (r.importance()!=null && importance==null) throw CommitmentException.invalid("importance cannot be null");
        if (r.flexibilityTier()!=null && flexibility==null) throw CommitmentException.invalid("flexibilityTier cannot be null");
        if (old==null && importance==null && category!=null) importance=category.getDefaultImportance();
        if (old==null && flexibility==null && category!=null) flexibility=category.getDefaultFlexibilityTier();
        return new Commitment.Fields(
            r.title()==null && old!=null ? old.getTitle() : string(r.title()),
            r.description()==null && old!=null ? old.getDescription() : string(r.description()),
            r.completionCriterion()==null && old!=null ? old.getCompletionCriterion() : string(r.completionCriterion()),
            r.ownDeadline()==null && old!=null ? old.getOwnDeadline() : deadline(r.ownDeadline()),
            milestone,goal,categoryId,importance,flexibility);
    }
    private Map<String,Object> snapshot(Commitment value) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",value.getId()); result.put("userId",value.getUserId());
        result.put("milestoneId",value.getMilestoneId()); result.put("goalId",value.getGoalId()); result.put("categoryId",value.getCategoryId());
        result.put("title",value.getTitle()); result.put("description",value.getDescription()); result.put("completionCriterion",value.getCompletionCriterion());
        result.put("ownDeadline",value.getOwnDeadline()==null ? null : value.getOwnDeadline().toString());
        result.put("importance",value.getImportance()); result.put("flexibilityTier",value.getFlexibilityTier()); result.put("workState",value.getWorkState());
        result.put("isHardConsequence",value.isHardConsequence()); result.put("userMovedFlag",value.isUserMovedFlag());
        result.put("currentCompletionPct",value.getCurrentCompletionPct()); result.put("createdAt",value.getCreatedAt().toString());
        return result;
    }
    private void event(Commitment value, String type, Map<String,Object> payload) {
        repository.flush();
        events.saveAndFlush(Event.forEntity("commitment",value.getId(),type,"user",null,mapper.writeValueAsString(payload)));
    }
}
