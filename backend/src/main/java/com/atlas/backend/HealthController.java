package com.atlas.backend;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bare-bones health endpoint. This exists only to prove the environment
 * (build, boot, request handling) works — not an Atlas product feature.
 * The full health/readiness contract is owned by a later Ops story.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

}
