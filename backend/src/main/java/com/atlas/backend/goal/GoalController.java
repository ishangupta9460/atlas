package com.atlas.backend.goal;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Protected Goal API. Identity is always taken from the authenticated JWT principal. */
@RestController
@RequestMapping("/goals")
public class GoalController {
    private final GoalService goalService;
    public GoalController(GoalService goalService) { this.goalService = goalService; }

    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidQuery(Exception ignored) {
        return ResponseEntity.badRequest().body(java.util.Map.of(
                "error_code", "VALIDATION_ERROR", "message", "Invalid goal query"));
    }

    @GetMapping
    public GoalService.GoalPage list(@AuthenticationPrincipal User user,
            @RequestParam(required = false) @Min(1) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return goalService.list(user.getId(), cursor, limit);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(user.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.get(user.getId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(goalService.update(user.getId(), id, request));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<GoalResponse> pause(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.pause(user.getId(), id));
    }

    @PostMapping("/{id}/abandon")
    public ResponseEntity<GoalResponse> abandon(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.abandon(user.getId(), id));
    }

    @PostMapping("/{id}/risk-response")
    public ResponseEntity<GoalResponse> resolveRisk(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.resolveRisk(user.getId(), id));
    }
}
