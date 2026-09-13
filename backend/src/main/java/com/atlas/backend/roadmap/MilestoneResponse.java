package com.atlas.backend.roadmap;

public record MilestoneResponse(Long id, Long roadmapId, String title, Integer order) {
    static MilestoneResponse from(Milestone milestone) {
        return new MilestoneResponse(milestone.getId(), milestone.getRoadmapId(), milestone.getTitle(), milestone.getDisplayOrder());
    }
}
