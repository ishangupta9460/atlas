package com.atlas.backend.ai;

public final class AiAuthenticationException extends AiException {
    public AiAuthenticationException(int status) {
        super("AI provider authentication failed", AiFailureCategory.AUTHENTICATION, false, status);
    }
}
