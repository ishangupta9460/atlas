package com.atlas.backend.ai;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiProviderTest {
    private static final String KEY = "synthetic-test-credential";
    private static final String PROMPT = "throwaway-test-prompt";
    private static final String URL = "https://example.invalid/v1beta/models/test-model:generateContent";
    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiProvider provider;
    private AiStructuredRequest request;

    @BeforeEach void setup() {
        var p = new AiProperties();
        p.setApiKey(KEY); p.setModel("test-model"); p.setBaseUrl("https://example.invalid");
        var builder = AiConfiguration.configureTimeouts(RestClient.builder(), p);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiProvider(builder, p, mapper);
        request = new AiStructuredRequest(PROMPT, new AiQuestionValidator().schema());
    }
    @AfterEach void verify() { server.verify(); MDC.clear(); }
    @Test void structuredRequestAndResponse() {
        MDC.put("correlationId", "test-correlation");
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", KEY)).andExpect(header("X-Correlation-ID", "test-correlation"))
            .andExpect(req -> {
                assertThat(req.getURI().toString()).doesNotContain(KEY).doesNotContain("?");
                var body = mapper.readTree(((MockClientHttpRequest) req).getBodyAsString());
                assertThat(body.at("/contents/0/parts/0/text").asString()).isEqualTo(PROMPT);
                assertThat(body.at("/generationConfig/responseMimeType").asString()).isEqualTo("application/json");
                assertThat(body.at("/generationConfig/responseSchema")).isEqualTo(mapper.valueToTree(request.responseSchema()));
                assertThat(body.at("/generationConfig/responseSchema/properties/type/enum/0").asString()).isEqualTo("question");
            }).andRespond(withSuccess(envelope("{}"), MediaType.APPLICATION_JSON));
        assertThat(provider.generateStructured(request).jsonText()).isEqualTo("{}");
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 400, 404, 500, 503, 302})
    void mapsHttpFailuresWithoutLeakingBody(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)).body(KEY + PROMPT));
        AiException ex = catchThrowableOfType(() -> provider.generateStructured(request), AiException.class);
        Class<? extends AiException> expected = status == 401 || status == 403 ? AiAuthenticationException.class
            : status == 429 ? AiRateLimitException.class : AiProviderResponseException.class;
        assertThat(ex).isInstanceOf(expected).hasNoCause();
        assertThat(ex.httpStatus()).isEqualTo(status);
        assertThat(ex.retryable()).isEqualTo(status == 429 || status >= 500);
        assertThat(ex.getMessage()).doesNotContain(KEY, PROMPT);
    }
    @ParameterizedTest @ValueSource(strings = {"", "null", "[]", "garbage", "{}", "{\"candidates\":[]}",
        "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
        "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
        "{\"candidates\":[{\"finishReason\":\"STOP\"}]}",
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\" \"}]}}]}",
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":7}]}}]}"})
    void rejectsUnusableEnvelopes(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.generateStructured(request)).isInstanceOf(AiProviderResponseException.class);
    }
    @Test void blockedEvenWithCandidate() {
        var root = (tools.jackson.databind.node.ObjectNode) mapper.readTree(envelope("{}"));
        root.putObject("promptFeedback").put("blockReason", "SAFETY");
        server.expect(requestTo(URL)).andRespond(withSuccess(mapper.writeValueAsString(root), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.generateStructured(request)).isInstanceOf(AiProviderResponseException.class);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ioFailuresAreSafeAndRetryable(boolean timeout) {
        IOException failure = timeout ? new SocketTimeoutException(KEY + PROMPT) : new IOException(KEY + PROMPT);
        server.expect(requestTo(URL)).andRespond(withException(failure));
        var ex = catchThrowableOfType(() -> provider.generateStructured(request), AiProviderRequestException.class);
        assertThat(ex.retryable()).isTrue();
        assertThat(ex.category()).isEqualTo(AiFailureCategory.PROVIDER_REQUEST);
        assertThat(ex).hasNoCause();
        assertThat(ex.getMessage()).doesNotContain(KEY, PROMPT);
    }
    private String envelope(String text) {
        return mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
            "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void responseStreamIoFailuresAreRetryable(boolean timeout) {
        server.expect(requestTo(URL)).andRespond(req -> new org.springframework.mock.http.client.MockClientHttpResponse(
            new java.io.InputStream() {
                @Override public int read() throws IOException {
                    if (timeout) throw new SocketTimeoutException(KEY + PROMPT);
                    throw new IOException(KEY + PROMPT);
                }
            }, org.springframework.http.HttpStatus.OK));
        var ex = catchThrowableOfType(() -> provider.generateStructured(request), AiProviderRequestException.class);
        assertThat(ex.retryable()).isTrue();
        assertThat(ex).hasNoCause();
        assertThat(ex.getMessage()).doesNotContain(KEY, PROMPT);
    }
}
