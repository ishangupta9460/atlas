package com.atlas.backend.ai;

public final class AiProviderRequestException extends AiException {
    public AiProviderRequestException() {
        super("AI provider request failed", AiFailureCategory.PROVIDER_REQUEST, true, null);
    }
}
