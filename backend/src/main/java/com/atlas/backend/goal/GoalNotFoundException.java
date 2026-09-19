package com.atlas.backend.goal;

public class GoalNotFoundException extends RuntimeException {
    public GoalNotFoundException() { super("Goal not found"); }
}
