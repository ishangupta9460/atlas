package com.atlas.backend.dependency;

import com.atlas.backend.user.User;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/commitments/{id}/dependencies")
public class DependencyController {
    private final DependencyService service;
    public DependencyController(DependencyService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DependencyResponse> add(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody AddDependencyRequest request) {
        var result = service.add(user.getId(), id, request.blockingId());
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.body());
    }

    @GetMapping
    public List<DependencyResponse> list(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.listInbound(user.getId(), id);
    }

    @DeleteMapping("/{blockingCommitmentId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long blockingCommitmentId) {
        service.remove(user.getId(), id, blockingCommitmentId);
        return ResponseEntity.noContent().build();
    }
}
