package com.atlas.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final AiProperties properties;
    private final ObjectProvider<AiProvider> providers;
    private final AiQuestionValidator validator;

    public AiService(AiProperties properties, ObjectProvider<AiProvider> providers, AiQuestionValidator validator) {
        this.properties = properties;
        this.providers = providers;
        this.validator = validator;
    }
    public boolean isAvailable() { return properties.isAvailable(); }
    public String unavailableReason() { return properties.unavailableReason(); }

    public AiQuestion askQuestion(String prompt) {
        long started = System.nanoTime();
        try {
            if (!isAvailable()) throw new AiNotConfiguredException();
            AiProvider provider = providers.getIfAvailable();
            if (provider == null) throw new AiNotConfiguredException();
            AiProviderResult response = provider.generateStructured(new AiStructuredRequest(prompt, validator.schema()));
            // The minimal provider-neutral result carries no transport metadata; do not invent a 200 status.
            AiQuestion question = validator.validate(response == null ? null : response.jsonText());
            log.atInfo().addKeyValue("category", "SUCCESS").addKeyValue("provider", properties.getProvider())
                .addKeyValue("model", properties.getModel()).addKeyValue("status", "unreported")
                .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                .addKeyValue("retryable", false).log("AI call completed");
            return question;
        } catch (AiException ex) {
            Integer status = ex.httpStatus();
            log.atWarn().addKeyValue("category", ex.category()).addKeyValue("provider", properties.getProvider())
                .addKeyValue("model", properties.getModel()).addKeyValue("status", status == null ? "unreported" : status)
                .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                .addKeyValue("retryable", ex.retryable()).log("AI call failed");
            throw ex;
        }
    }
}
