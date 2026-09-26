package com.atlas.backend.scheduling;

import com.atlas.backend.user.User;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchedulingController {
    private final SchedulingPipeline pipeline;
    public SchedulingController(SchedulingPipeline pipeline) { this.pipeline = pipeline; }
    @PostMapping(value = "/schedule/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public String generate(@AuthenticationPrincipal User user, @RequestHeader("Idempotency-Key") String key,
                           @RequestBody SchedulingPipeline.Request request) {
        return pipeline.generate(user.getId(), key, request);
    }
}
