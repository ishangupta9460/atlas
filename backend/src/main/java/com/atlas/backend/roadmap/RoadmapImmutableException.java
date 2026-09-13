package com.atlas.backend.roadmap;

public class RoadmapImmutableException extends RuntimeException {
    public RoadmapImmutableException() { super("Roadmaps have no mutable fields after creation"); }
}
