package com.atlas.backend.roadmap;

public class MilestoneNotFoundException extends RuntimeException {
    public MilestoneNotFoundException() { super("Milestone not found"); }
}
