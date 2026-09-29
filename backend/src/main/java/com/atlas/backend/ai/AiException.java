package com.atlas.backend.ai;

public abstract class AiException extends RuntimeException {
    private final AiFailureCategory category;
    private final boolean retryable;
    private final Integer httpStatus;

    protected AiException(String message, AiFailureCategory category, boolean retryable, Integer httpStatus) {
        // Do not retain provider/transport causes: their messages can contain credentials or content.
        super(message);
        this.category = category;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }
    public AiFailureCategory category() { return category; }
    public boolean retryable() { return retryable; }
    public Integer httpStatus() { return httpStatus; }
}
