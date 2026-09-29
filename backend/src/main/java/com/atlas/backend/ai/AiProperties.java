package com.atlas.backend.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("atlas.ai")
public class AiProperties {
    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey = "";
    @NotBlank private String model = "gemini-3.5-flash-lite";
    @NotBlank private String baseUrl = "https://generativelanguage.googleapis.com";
    @Positive private int connectTimeoutMs = 5000;
    @Positive private int readTimeoutMs = 30000;

    public boolean isAvailable() { return unavailableReason() == null; }
    public String unavailableReason() {
        if (!enabled) return "disabled";
        if (apiKey == null || apiKey.isBlank()) return "api key not set";
        if (!"gemini".equals(provider)) return "unsupported provider";
        return null;
    }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { readTimeoutMs = value; }
    @Override public String toString() { return "AiProperties[enabled=" + enabled + "]"; }
}
