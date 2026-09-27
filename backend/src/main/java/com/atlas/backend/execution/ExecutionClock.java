package com.atlas.backend.execution;

import java.time.Instant;
import org.springframework.stereotype.Component;

/** Server time seam shared by runtime reads and transitions. */
@Component
public class ExecutionClock {
    public Instant now() { return Instant.now(); }
}
