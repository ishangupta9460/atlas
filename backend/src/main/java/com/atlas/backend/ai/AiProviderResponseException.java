package com.atlas.backend.ai;

public final class AiProviderResponseException extends AiException {
    public AiProviderResponseException(Integer status, boolean retryable) {
        super("AI provider returned an unusable response", AiFailureCategory.PROVIDER_RESPONSE, retryable, status);
    }
}
