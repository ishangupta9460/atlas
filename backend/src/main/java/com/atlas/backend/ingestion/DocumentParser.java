package com.atlas.backend.ingestion;

import java.util.List;

/** Extraction adapters can add PDF/Word later without changing the review/approval contract. */
public interface DocumentParser {
    List<ImportNode> parse(String text);
}
