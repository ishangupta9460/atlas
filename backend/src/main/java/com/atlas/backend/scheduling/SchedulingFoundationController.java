package com.atlas.backend.scheduling;

import com.atlas.backend.user.User;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchedulingFoundationController {
    private final WorkingHoursService hours;
    private final SchedulingFoundationService foundation;
    public SchedulingFoundationController(WorkingHoursService hours, SchedulingFoundationService foundation) {
        this.hours = hours; this.foundation = foundation;
    }
    public record AvailabilityResponse(boolean configured, String timezone, List<WorkingHours.Window> windows) {
        static AvailabilityResponse from(WorkingHours hours) {
            return hours == null ? new AvailabilityResponse(false, null, List.of())
                    : new AvailabilityResponse(true, hours.timezone(), hours.windows());
        }
    }
    @GetMapping("/users/me/working-hours")
    public AvailabilityResponse get(@AuthenticationPrincipal User user) {
        return AvailabilityResponse.from(hours.get(user.getId()));
    }
    @PutMapping("/users/me/working-hours")
    public AvailabilityResponse put(@AuthenticationPrincipal User user, @RequestBody WorkingHours request) {
        return AvailabilityResponse.from(hours.put(user.getId(), request));
    }
    @GetMapping("/schedule/candidates")
    public SchedulingFoundationService.Foundation candidates(@AuthenticationPrincipal User user,
            @RequestParam Instant startTime, @RequestParam Instant endTime, @RequestParam int workMinutes) {
        return foundation.query(user.getId(), startTime, endTime, workMinutes);
    }
}
