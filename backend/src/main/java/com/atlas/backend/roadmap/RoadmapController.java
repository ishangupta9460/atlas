package com.atlas.backend.roadmap;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protected direct entity routes; import-pipeline routes remain owned by ROAD stories. */
@RestController
public class RoadmapController {
    private final RoadmapService roadmapService;
    public RoadmapController(RoadmapService roadmapService) { this.roadmapService = roadmapService; }

    @GetMapping("/goals/{goalId}/roadmap")
    public java.util.Map<String, RoadmapResponse> getForGoal(@AuthenticationPrincipal User user, @PathVariable Long goalId) {
        return java.util.Collections.singletonMap("roadmap", roadmapService.getForGoal(user.getId(), goalId));
    }

    @PostMapping("/goals/{goalId}/roadmaps")
    public ResponseEntity<RoadmapResponse> createRoadmap(@AuthenticationPrincipal User user, @PathVariable Long goalId,
                                                           @Valid @RequestBody CreateRoadmapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roadmapService.createRoadmap(user.getId(), goalId, request));
    }
    @GetMapping("/roadmaps/{id}")
    public ResponseEntity<RoadmapResponse> getRoadmap(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.getRoadmap(user.getId(), id));
    }
    @PatchMapping("/roadmaps/{id}")
    public ResponseEntity<RoadmapResponse> updateRoadmap(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.updateRoadmap(user.getId(), id));
    }
    @PostMapping("/roadmaps/{roadmapId}/milestones")
    public ResponseEntity<MilestoneResponse> createMilestone(@AuthenticationPrincipal User user, @PathVariable Long roadmapId,
                                                               @Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roadmapService.createMilestone(user.getId(), roadmapId, request));
    }
    @GetMapping("/milestones/{id}")
    public ResponseEntity<MilestoneResponse> getMilestone(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(roadmapService.getMilestone(user.getId(), id));
    }
    @PatchMapping("/milestones/{id}")
    public ResponseEntity<MilestoneResponse> updateMilestone(@AuthenticationPrincipal User user, @PathVariable Long id,
                                                               @Valid @RequestBody UpdateMilestoneRequest request) {
        return ResponseEntity.ok(roadmapService.updateMilestone(user.getId(), id, request));
    }
}
