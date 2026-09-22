package com.atlas.backend.commitment;

import com.atlas.backend.user.User;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/goals/{goalId}/commitments")
public class GoalCommitmentController {
    private final CommitmentService service;
    public GoalCommitmentController(CommitmentService service) { this.service = service; }

    @GetMapping
    public CommitmentService.CommitmentPage list(@AuthenticationPrincipal User user, @PathVariable @Min(1) Long goalId,
            @RequestParam(required = false) @Min(1) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.listForGoal(user.getId(), goalId, cursor, limit);
    }

    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidQuery(Exception ignored) {
        return ResponseEntity.badRequest().body(java.util.Map.of(
                "error_code", "VALIDATION_ERROR", "message", "Invalid commitment query"));
    }
}
