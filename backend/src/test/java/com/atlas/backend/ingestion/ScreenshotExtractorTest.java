package com.atlas.backend.ingestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenshotExtractorTest {
    @Test void unavailableOcrReturnsAnHonestReviewFallback(){
        var result=new TesseractScreenshotExtractor("atlas-deliberately-missing-ocr-executable").extract(new byte[]{1,2,3});
        assertEquals("",result.text());assertTrue(result.warning().contains("unavailable"));
    }
}
