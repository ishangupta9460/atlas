package com.atlas.backend.roadmap;

public class RoadmapNotFoundException extends RuntimeException {
    public RoadmapNotFoundException() { super("Roadmap not found"); }
}
