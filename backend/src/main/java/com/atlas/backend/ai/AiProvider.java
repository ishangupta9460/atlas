package com.atlas.backend.ai;

public interface AiProvider {
    AiProviderResult generateStructured(AiStructuredRequest request);
}
