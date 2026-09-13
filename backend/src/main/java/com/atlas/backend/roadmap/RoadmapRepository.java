package com.atlas.backend.roadmap;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {
    Optional<Roadmap> findByIdAndGoalId(Long id, Long goalId);
}
