package com.atlas.backend.recurringintention;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recurring-intentions")
public class RecurringIntentionController {
    private final RecurringIntentionService recurringIntentionService;
    public RecurringIntentionController(RecurringIntentionService recurringIntentionService) { this.recurringIntentionService = recurringIntentionService; }

    @PostMapping public ResponseEntity<RecurringIntentionResponse> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRecurringIntentionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringIntentionService.create(user.getId(), request));
    }
    @GetMapping("/{id}") public ResponseEntity<RecurringIntentionResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(recurringIntentionService.get(user.getId(), id));
    }
    @PatchMapping("/{id}/target") public ResponseEntity<RecurringIntentionResponse> updateTarget(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody UpdateRecurringIntentionTargetRequest request) {
        return ResponseEntity.ok(recurringIntentionService.updateTarget(user.getId(), id, request));
    }
    @PostMapping("/{id}/instances/complete") public ResponseEntity<RecurringIntentionResponse> completeInstance(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(recurringIntentionService.completeInstance(user.getId(), id));
    }
    @PostMapping("/{id}/instances/miss") public ResponseEntity<RecurringIntentionResponse> reportMissedInstance(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(recurringIntentionService.reportMissedInstance(user.getId(), id));
    }
}
