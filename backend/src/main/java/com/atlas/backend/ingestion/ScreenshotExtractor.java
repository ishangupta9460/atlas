package com.atlas.backend.ingestion;

public interface ScreenshotExtractor {
    record Extraction(String text,String warning) {}
    Extraction extract(byte[] image);
}
