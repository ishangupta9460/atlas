package com.atlas.backend.ai;

public final class AiResponseValidationException extends AiException {
    public AiResponseValidationException() {
        super("AI response failed validation", AiFailureCategory.VALIDATION, false, null);
    }
}
