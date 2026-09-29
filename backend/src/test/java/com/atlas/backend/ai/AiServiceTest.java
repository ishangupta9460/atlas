package com.atlas.backend.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiServiceTest {
    private static final String VALID = "{\"type\":\"question\",\"question\":\"Which topic?\",\"reason\":\"To narrow scope\"}";
    private final AiProvider provider = mock(AiProvider.class);
    private final AiProperties properties = new AiProperties();

    private AiService service() {
        var beans = new StaticListableBeanFactory(); beans.addBean("provider", provider);
        return new AiService(properties, beans.getBeanProvider(AiProvider.class), new AiQuestionValidator());
    }
    @Test void unavailableDoesNotCallProvider() {
        var service = service();
        assertThat(service.isAvailable()).isFalse();
        assertThat(service.unavailableReason()).isEqualTo("api key not set");
        assertThatThrownBy(() -> service.askQuestion("throwaway")).isInstanceOf(AiNotConfiguredException.class);
        verifyNoInteractions(provider);
    }
    @Test void validOutput() {
        properties.setApiKey("synthetic-test-credential");
        when(provider.generateStructured(any())).thenReturn(new AiProviderResult(VALID));
        assertThat(service().askQuestion("throwaway")).isEqualTo(new AiQuestion("question", "Which topic?", "To narrow scope"));
        verify(provider, times(1)).generateStructured(any());
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"", "not JSON", "[]", "null", "7", "{}",
        "{\"type\":\"question\",\"question\":5,\"reason\":\"reason\"}",
        "{\"type\":\"question\",\"question\":\" \",\"reason\":\"reason\"}",
        "{\"type\":\"question\",\"question\":\"Q\",\"reason\":\" \"}",
        "{\"type\":\"clarifying\",\"question\":\"Q\",\"reason\":\"reason\"}",
        "{\"type\":\"question\",\"question\":\"Q\",\"reason\":\"reason\",\"commit\":true}",
        "{\"type\":\"question\",\"question\":\"Q\",\"reason\":null}",
        "{\"type\":\"question\",\"question\":\"Q\",\"reason\":\"reason\",\"type\":\"question\"}",
        "{\"type\":\"question\",\"question\":\"Q\",\"reason\":\"reason\"} {}"})
    void rejectsInvalidOutput(String json) {
        properties.setApiKey("synthetic-test-credential");
        when(provider.generateStructured(any())).thenReturn(new AiProviderResult(json));
        var ex = catchThrowableOfType(() -> service().askQuestion("throwaway"), AiResponseValidationException.class);
        assertThat(ex.category()).isEqualTo(AiFailureCategory.VALIDATION);
        assertThat(ex.retryable()).isFalse();
        assertThat(ex).hasNoCause();
    }
    @ParameterizedTest @ValueSource(strings = {"type", "question", "reason"})
    void rejectsOversizedStrings(String field) {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var root = (tools.jackson.databind.node.ObjectNode) mapper.readTree(VALID);
        root.put(field, "x".repeat(2001));
        assertThatThrownBy(() -> new AiQuestionValidator().validate(mapper.writeValueAsString(root)))
            .isInstanceOf(AiResponseValidationException.class);
    }
    @Test void logsOnlyMetadataAndPropagatesFailureUnchanged() {
        properties.setApiKey("synthetic-test-credential");
        var logger = (Logger) LoggerFactory.getLogger(AiService.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            when(provider.generateStructured(any())).thenReturn(new AiProviderResult(VALID));
            service().askQuestion("throwaway-private-prompt");
            var failure = new AiRateLimitException();
            when(provider.generateStructured(any())).thenThrow(failure);
            assertThatThrownBy(() -> service().askQuestion("throwaway-private-prompt")).isSameAs(failure);
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.WARN);
            for (var event : appender.list) {
                assertThat(event.getThrowableProxy()).isNull();
                String rendered = event.getFormattedMessage() + event.getKeyValuePairs();
                assertThat(rendered).contains("category", "model", "status", "durationMs", "retryable")
                    .doesNotContain("synthetic-test-credential", "throwaway-private-prompt", "Which topic?", "To narrow scope");
            }
            assertThat(appender.list.get(1).getKeyValuePairs().toString()).contains("429", "RATE_LIMITED");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void startupReportsOnceWithoutCredential() {
        var logger = (Logger) LoggerFactory.getLogger(AiStartupReporter.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            new AiStartupReporter(properties).run(null);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("AI disabled: api key not set");
            properties.setApiKey("synthetic-test-credential");
            appender.list.clear();
            new AiStartupReporter(properties).run(null);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("AI enabled: provider=gemini model=gemini-3.5-flash-lite");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
