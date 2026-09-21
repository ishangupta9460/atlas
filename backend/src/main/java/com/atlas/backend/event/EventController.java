package com.atlas.backend.event;

import com.atlas.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventQueryService service;
    public EventController(EventQueryService service) { this.service = service; }

    @GetMapping
    public EventQueryService.Page recent(@AuthenticationPrincipal User user,
            @RequestParam("entity_type") String entityType, @RequestParam("entity_id") Long entityId,
            @RequestParam(required = false) String actor, @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.recent(user.getId(), entityType, entityId, actor, cursor, limit);
    }
}
