package com.atlas.backend.scheduling;

import com.atlas.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/me/capacity")
public class CapacityConfigController {
    private final CapacityConfigService service;
    public CapacityConfigController(CapacityConfigService service) { this.service = service; }

    public record PolicyRequest(java.math.BigDecimal workableFraction, Integer bufferMinutes,
                                Integer continuousWorkMinutes, Integer breakMinutes) {
        CapacityCalculator.Policy policy() {
            if (workableFraction == null || bufferMinutes == null || continuousWorkMinutes == null || breakMinutes == null)
                throw new IllegalArgumentException("Provide every capacity policy field");
            return new CapacityCalculator.Policy(workableFraction, bufferMinutes, continuousWorkMinutes, breakMinutes);
        }
    }

    @GetMapping
    public CapacityCalculator.Policy get(@AuthenticationPrincipal User user) { return service.get(user.getId()); }

    @PutMapping
    public CapacityCalculator.Policy put(@AuthenticationPrincipal User user, @RequestBody PolicyRequest request) {
        return service.put(user.getId(), request.policy());
    }
}
