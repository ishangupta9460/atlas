package com.atlas.backend.ai;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTest {
    @Test void availabilityAndRedaction() {
        var p = new AiProperties();
        assertThat(p.isAvailable()).isFalse();
        assertThat(p.unavailableReason()).isEqualTo("api key not set");
        p.setApiKey(" ");
        assertThat(p.isAvailable()).isFalse();
        p.setApiKey("synthetic-test-credential");
        assertThat(p.isAvailable()).isTrue();
        assertThat(p.unavailableReason()).isNull();
        assertThat(p.toString()).doesNotContain("synthetic-test-credential");
        p.setProvider("unsupported");
        assertThat(p.unavailableReason()).isEqualTo("unsupported provider");
        assertThat(p.isAvailable()).isFalse();
        p.setEnabled(false);
        assertThat(p.unavailableReason()).isEqualTo("disabled");
        assertThat(p.isAvailable()).isFalse();
    }
    @Test void blankCredentialIsValid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new AiProperties())).isEmpty();
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"connect-timeout-ms=0", "connect-timeout-ms=-1", "read-timeout-ms=0", "read-timeout-ms=-1", "model=", "base-url="})
    void invalidConfigurationFailsBinding(String property) {
        context().withPropertyValues("atlas.ai." + property).run(ctx -> assertThat(ctx).hasFailed());
    }
    @Test void contextStartsWithoutAiEnvironment() {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(AiProvider.class);
            assertThat(ctx.getBean(AiProperties.class).isAvailable()).isFalse();
        });
    }
    @Test void unsupportedProviderHasNoProviderBeanAndStillStarts() {
        context().withPropertyValues("atlas.ai.provider=unsupported", "atlas.ai.api-key=synthetic-test-credential")
            .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(AiProvider.class));
    }
    @Test void contextStartsWithoutAutoConfiguredRestClientBuilder() {
        new ApplicationContextRunner().withUserConfiguration(AiConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().remove("systemEnvironment"))
            .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(AiProvider.class));
    }
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(AiConfiguration.class, Dependencies.class)
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().remove("systemEnvironment"));
    }
    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean RestClient.Builder builder() { return RestClient.builder(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    }
}
