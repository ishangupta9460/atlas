package com.atlas.backend.task;

/** Deliberately used for both absent and foreign-owned tasks to avoid disclosure. */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException() {
        super("Task not found");
    }
}
