package com.atlas.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AiStartupReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AiStartupReporter.class);
    private final AiProperties properties;
    public AiStartupReporter(AiProperties properties) { this.properties = properties; }
    @Override public void run(ApplicationArguments args) {
        if (properties.isAvailable()) {
            log.info("AI enabled: provider={} model={}", properties.getProvider(), properties.getModel());
        } else {
            log.info("AI disabled: {}", properties.unavailableReason());
        }
    }
}
