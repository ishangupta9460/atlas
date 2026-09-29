package com.atlas.backend.ai;

public final class AiNotConfiguredException extends AiException {
    public AiNotConfiguredException() {
        super("AI is not configured", AiFailureCategory.NOT_CONFIGURED, false, null);
    }
}
