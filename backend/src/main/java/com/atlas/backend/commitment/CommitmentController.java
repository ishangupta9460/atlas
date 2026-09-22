package com.atlas.backend.commitment;

import com.atlas.backend.user.User;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/commitments")
public class CommitmentController {
    private final CommitmentService service;
    public CommitmentController(CommitmentService service) { this.service=service; }
    @GetMapping
    public CommitmentService.CommitmentPage search(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue="") @Size(max=255) String q,
            @RequestParam(required=false) @Min(1) Long excludeId,
            @RequestParam(required=false) @Min(1) Long cursor,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit) {
        return service.search(user.getId(), q, excludeId, cursor, limit);
    }
    @PostMapping public ResponseEntity<CommitmentResponse> create(@AuthenticationPrincipal User user, @RequestBody CommitmentRequest request) {
        return ResponseEntity.status(201).body(service.create(user.getId(),request));
    }
    @GetMapping("/{id}") public CommitmentResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) { return service.get(user.getId(),id); }
    @PatchMapping("/{id}") public CommitmentResponse update(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestBody CommitmentRequest request) {
        return service.update(user.getId(),id,request);
    }
}
