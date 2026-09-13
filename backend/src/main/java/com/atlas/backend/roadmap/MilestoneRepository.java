package com.atlas.backend.roadmap;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByRoadmapIdOrderByDisplayOrderAscIdAsc(Long roadmapId);
    Optional<Milestone> findByIdAndRoadmapId(Long id, Long roadmapId);
}
