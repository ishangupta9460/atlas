package com.atlas.backend.commitment;

public class CommitmentException extends RuntimeException {
    private final int status;
    private final String code;
    private CommitmentException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static CommitmentException invalid(String message) { return new CommitmentException(400, "VALIDATION_ERROR", message); }
    public static CommitmentException missing() { return new CommitmentException(404, "COMMITMENT_NOT_FOUND", "Commitment or related entity not found"); }
    public static CommitmentException state() { return new CommitmentException(409, "INVALID_COMMITMENT_STATE", "Operation is not allowed in the current Work State"); }
}
