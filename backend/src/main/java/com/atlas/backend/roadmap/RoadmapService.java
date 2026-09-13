package com.atlas.backend.roadmap;

import com.atlas.backend.goal.Goal;
import com.atlas.backend.goal.GoalNotFoundException;
import com.atlas.backend.goal.GoalRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns structural Roadmap/Milestone CRUD and user ownership-chain checks. */
@Service
public class RoadmapService {
    private final GoalRepository goalRepository;
    private final RoadmapRepository roadmapRepository;
    private final MilestoneRepository milestoneRepository;

    public RoadmapService(GoalRepository goalRepository, RoadmapRepository roadmapRepository,
                          MilestoneRepository milestoneRepository) {
        this.goalRepository = goalRepository;
        this.roadmapRepository = roadmapRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @Transactional
    public RoadmapResponse createRoadmap(Long userId, Long goalId, CreateRoadmapRequest request) {
        findOwnedGoal(userId, goalId);
        try {
            Roadmap roadmap = roadmapRepository.saveAndFlush(Roadmap.create(goalId, request.source()));
            return response(roadmap);
        } catch (DataIntegrityViolationException ex) {
            throw new RoadmapAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmap(Long userId, Long roadmapId) { return response(findOwnedRoadmap(userId, roadmapId)); }

    /** Source is immutable and the Roadmap entity owns no other mutable fields. */
    @Transactional(readOnly = true)
    public RoadmapResponse updateRoadmap(Long userId, Long roadmapId) {
        findOwnedRoadmap(userId, roadmapId);
        throw new RoadmapImmutableException();
    }

    @Transactional
    public MilestoneResponse createMilestone(Long userId, Long roadmapId, CreateMilestoneRequest request) {
        findOwnedRoadmap(userId, roadmapId);
        return MilestoneResponse.from(milestoneRepository.save(Milestone.create(roadmapId, request.title(), request.order())));
    }

    @Transactional(readOnly = true)
    public MilestoneResponse getMilestone(Long userId, Long milestoneId) {
        return MilestoneResponse.from(findOwnedMilestone(userId, milestoneId));
    }

    @Transactional
    public MilestoneResponse updateMilestone(Long userId, Long milestoneId, UpdateMilestoneRequest request) {
        Milestone milestone = findOwnedMilestone(userId, milestoneId);
        milestone.update(request.getTitle(), request.getOrder());
        return MilestoneResponse.from(milestone);
    }

    private Goal findOwnedGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(GoalNotFoundException::new);
    }
    private Roadmap findOwnedRoadmap(Long userId, Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId).orElseThrow(RoadmapNotFoundException::new);
        findOwnedGoal(userId, roadmap.getGoalId());
        return roadmap;
    }
    private Milestone findOwnedMilestone(Long userId, Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId).orElseThrow(MilestoneNotFoundException::new);
        findOwnedRoadmap(userId, milestone.getRoadmapId());
        return milestone;
    }
    private RoadmapResponse response(Roadmap roadmap) {
        return RoadmapResponse.from(roadmap, milestoneRepository.findAllByRoadmapIdOrderByDisplayOrderAscIdAsc(roadmap.getId()));
    }
}
