package com.atlas.backend.fixedcommitment;

import com.atlas.backend.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fixed-commitments")
public class FixedCommitmentController {
    private final FixedCommitmentService service;
    public FixedCommitmentController(FixedCommitmentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<FixedCommitmentResponse> create(@AuthenticationPrincipal User user, @RequestBody CreateFixedCommitmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), request));
    }

    @GetMapping("/{id}")
    public FixedCommitmentResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) { return service.get(user.getId(), id); }

    @PatchMapping("/{id}")
    public FixedCommitmentResponse update(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestBody UpdateFixedCommitmentRequest request) {
        return service.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
