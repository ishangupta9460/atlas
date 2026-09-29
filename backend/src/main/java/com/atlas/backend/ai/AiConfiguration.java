package com.atlas.backend.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "atlas.ai", name = "provider", havingValue = "gemini", matchIfMissing = true)
    AiProvider aiProvider(AiProperties properties, ObjectProvider<RestClient.Builder> builders, ObjectMapper mapper) {
        // Configure before binding/building the provider, so a mock factory can replace this in tests.
        return new GeminiProvider(configureTimeouts(builders.getIfAvailable(RestClient::builder), properties), properties, mapper);
    }

    static RestClient.Builder configureTimeouts(RestClient.Builder builder, AiProperties properties) {
        var client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return builder.requestFactory(factory);
    }
}
