package com.atlas.backend.ai;

public final class AiRateLimitException extends AiException {
    public AiRateLimitException() {
        super("AI provider rate limit reached", AiFailureCategory.RATE_LIMITED, true, 429);
    }
}
