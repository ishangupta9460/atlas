package com.atlas.backend.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Manual owner-only test. Two opt-ins prevent accidental network access in ordinary test runs. */
@EnabledIfEnvironmentVariable(named = "ATLAS_AI_SMOKE", matches = "true")
@EnabledIfSystemProperty(named = "atlas.ai.smoke", matches = "true")
@DisabledIfEnvironmentVariable(named = "CI", matches = "(?i)true")
class AiLiveSmokeTest {
    @Test void throwawayQuestion() {
        var properties = new AiProperties();
        properties.setApiKey(System.getenv("ATLAS_AI_API_KEY"));
        String model = System.getenv("ATLAS_AI_MODEL");
        if (model != null && !model.isBlank()) properties.setModel(model);
        assertThat(properties.isAvailable()).as("AI must be configured for the manual smoke test").isTrue();
        var builder = AiConfiguration.configureTimeouts(RestClient.builder(), properties);
        var provider = new GeminiProvider(builder, properties, new ObjectMapper());
        var beans = new StaticListableBeanFactory(); beans.addBean("provider", provider);
        var service = new AiService(properties, beans.getBeanProvider(AiProvider.class), new AiQuestionValidator());
        var question = service.askQuestion("A user says: I want to learn Spring Boot. Ask ONE useful clarifying question and give a short reason.");
        assertThat(question.type()).isEqualTo("question");
    }
}
