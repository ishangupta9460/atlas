package com.atlas.backend.roadmap;

import java.time.Instant;
import java.util.List;

public record RoadmapResponse(Long id, Long goalId, String source, Instant createdAt,
                              List<MilestoneResponse> milestones) {
    static RoadmapResponse from(Roadmap roadmap, List<Milestone> milestones) {
        return new RoadmapResponse(roadmap.getId(), roadmap.getGoalId(), roadmap.getSource(), roadmap.getCreatedAt(),
                milestones.stream().map(MilestoneResponse::from).toList());
    }
}
