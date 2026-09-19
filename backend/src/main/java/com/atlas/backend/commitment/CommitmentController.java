package com.atlas.backend.commitment;

import com.atlas.backend.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/commitments")
public class CommitmentController {
    private final CommitmentService service;
    public CommitmentController(CommitmentService service) { this.service=service; }
    @PostMapping public ResponseEntity<CommitmentResponse> create(@AuthenticationPrincipal User user, @RequestBody CommitmentRequest request) {
        return ResponseEntity.status(201).body(service.create(user.getId(),request));
    }
    @GetMapping("/{id}") public CommitmentResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) { return service.get(user.getId(),id); }
    @PatchMapping("/{id}") public CommitmentResponse update(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestBody CommitmentRequest request) {
        return service.update(user.getId(),id,request);
    }
}
