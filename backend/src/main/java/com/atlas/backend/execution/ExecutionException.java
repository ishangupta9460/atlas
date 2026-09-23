package com.atlas.backend.execution;

public class ExecutionException extends RuntimeException {
    final int status;
    public ExecutionException(int status, String message) { super(message); this.status = status; }
    static ExecutionException missing() { return new ExecutionException(404, "Work window not found"); }
    static ExecutionException conflict(String message) { return new ExecutionException(409, message); }
}
