package com.atlas.backend.ai;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AiQuestionValidator {
    private static final int MAX_STRING_LENGTH = 2000;
    private final JsonMapper mapper = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public Map<String, Object> schema() {
        return Map.of("type", "OBJECT", "required", List.of("type", "question", "reason"),
            "properties", Map.of("type", Map.of("type", "STRING", "enum", List.of("question")),
                "question", Map.of("type", "STRING"), "reason", Map.of("type", "STRING")));
    }

    public AiQuestion validate(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) throw new AiResponseValidationException();
        try {
            var root = mapper.readTree(jsonText);
            if (root == null || !root.isObject() || root.size() != 3) throw new AiResponseValidationException();
            for (String field : List.of("type", "question", "reason")) {
                var value = root.path(field);
                if (!value.isString() || value.asString().isBlank() || value.asString().length() > MAX_STRING_LENGTH) {
                    throw new AiResponseValidationException();
                }
            }
            if (!"question".equals(root.path("type").asString())) throw new AiResponseValidationException();
            return new AiQuestion(root.path("type").asString(), root.path("question").asString(), root.path("reason").asString());
        } catch (JacksonException ex) {
            throw new AiResponseValidationException();
        }
    }
}
