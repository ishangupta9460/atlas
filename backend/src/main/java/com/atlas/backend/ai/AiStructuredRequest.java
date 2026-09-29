package com.atlas.backend.ai;

import java.util.Map;

public record AiStructuredRequest(String prompt, Map<String, Object> responseSchema) {
    @Override public String toString() { return "AiStructuredRequest[content omitted]"; }
}
