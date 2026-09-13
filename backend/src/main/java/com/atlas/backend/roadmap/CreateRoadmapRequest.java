package com.atlas.backend.roadmap;

import jakarta.validation.constraints.Pattern;

public record CreateRoadmapRequest(
        @Pattern(regexp = "user_interview|imported", message = "source must be user_interview or imported")
        String source) { }
