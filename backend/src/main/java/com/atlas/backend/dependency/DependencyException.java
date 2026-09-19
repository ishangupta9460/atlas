package com.atlas.backend.dependency;

public class DependencyException extends RuntimeException {
    private final int status;
    private final String code;
    private DependencyException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static DependencyException invalid(String message) {
        return new DependencyException(400, "VALIDATION_ERROR", message);
    }
    public static DependencyException missing() {
        return new DependencyException(404, "COMMITMENT_NOT_FOUND", "Commitment or related entity not found");
    }
    public static DependencyException cycle() {
        return new DependencyException(409, "DEPENDENCY_CYCLE", "Dependency would create a cycle");
    }
}
