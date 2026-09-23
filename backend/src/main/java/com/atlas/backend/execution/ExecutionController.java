package com.atlas.backend.execution;

import com.atlas.backend.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExecutionController {
    private final ExecutionService service;
    public ExecutionController(ExecutionService service) { this.service = service; }
    public record Placement(@NotNull @Positive Long commitmentId, @NotNull Instant startTime, @NotNull Instant endTime) {}
    public record Report(@NotBlank @Size(max=8000) String report,
                         @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=2) BigDecimal completionPct) {}
    @GetMapping("/execution")
    public ExecutionService.Workspace workspace(@AuthenticationPrincipal User user) { return service.workspace(user.getId()); }
    @PostMapping(value="/schedule/blocks", produces="application/json")
    public String place(@AuthenticationPrincipal User user, @RequestHeader("Idempotency-Key") String key,
                        @Valid @RequestBody Placement body) { return service.place(user.getId(), key, body); }
    @PostMapping(value="/schedule/blocks/{id}/move", produces="application/json")
    public String move(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                       @Valid @RequestBody Placement body) { return service.move(user.getId(), id, key, body); }
    @PostMapping(value="/blocks/{id}/session/{action:start|pause|resume}", produces="application/json")
    public String transition(@AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable String action,
                             @RequestHeader("Idempotency-Key") String key) { return service.transition(user.getId(), id, action, key, null); }
    @PostMapping(value="/blocks/{id}/session/finish", produces="application/json")
    public String finish(@AuthenticationPrincipal User user, @PathVariable Long id, @RequestHeader("Idempotency-Key") String key,
                         @Valid @RequestBody Report report) { return service.transition(user.getId(), id, "finish", key, report); }
}
