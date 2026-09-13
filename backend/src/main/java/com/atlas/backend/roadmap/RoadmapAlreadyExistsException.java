package com.atlas.backend.roadmap;

public class RoadmapAlreadyExistsException extends RuntimeException {
    public RoadmapAlreadyExistsException() { super("A Roadmap already exists for this Goal"); }
}
