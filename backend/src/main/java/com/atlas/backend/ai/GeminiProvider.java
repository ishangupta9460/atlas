package com.atlas.backend.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The sole owner of Gemini's HTTP envelope. No retries or domain dependencies. */
public class GeminiProvider implements AiProvider {
    private final RestClient client;
    private final AiProperties properties;
    private final ObjectMapper mapper;

    public GeminiProvider(RestClient.Builder builder, AiProperties properties, ObjectMapper mapper) {
        this.client = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override public AiProviderResult generateStructured(AiStructuredRequest request) {
        if (!properties.isAvailable()) throw new AiNotConfiguredException();
        var body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", request.prompt())))),
            "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", request.responseSchema()));
        try {
            return client.post().uri("/v1beta/models/{model}:generateContent", properties.getModel())
                .header("x-goog-api-key", properties.getApiKey())
                .headers(headers -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null && correlationId.matches("[A-Za-z0-9._-]{1,64}")) {
                        headers.set("X-Correlation-ID", correlationId);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((httpRequest, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 401 || status == 403) throw new AiAuthenticationException(status);
                    if (status == 429) throw new AiRateLimitException();
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new AiProviderResponseException(status, status >= 500 && status <= 599);
                    }
                    try {
                        return extract(mapper.readTree(response.getBody()), status);
                    } catch (JacksonIOException ex) {
                        throw new AiProviderRequestException();
                    } catch (JacksonException ex) {
                        throw new AiProviderResponseException(status, false);
                    } catch (IOException ex) {
                        throw new AiProviderRequestException();
                    }
                });
        } catch (ResourceAccessException ex) {
            throw new AiProviderRequestException();
        } catch (RestClientException ex) {
            throw new AiProviderResponseException(null, false);
        }
    }

    private AiProviderResult extract(JsonNode root, int status) {
        if (root == null || !root.isObject() || root.path("promptFeedback").has("blockReason")) {
            throw new AiProviderResponseException(status, false);
        }
        JsonNode candidate = root.path("candidates").path(0);
        JsonNode text = candidate.path("content").path("parts").path(0).path("text");
        if (!"STOP".equals(candidate.path("finishReason").asString()) || !text.isString() || text.asString().isBlank()) {
            throw new AiProviderResponseException(status, false);
        }
        return new AiProviderResult(text.asString());
    }
}
