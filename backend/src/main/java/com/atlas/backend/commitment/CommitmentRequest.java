package com.atlas.backend.commitment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;

/** JsonNode distinguishes omission, explicit null, and incorrect JSON types. */
public record CommitmentRequest(JsonNode title, JsonNode description, JsonNode completionCriterion,
        JsonNode ownDeadline, JsonNode milestoneId, JsonNode goalId, JsonNode categoryId,
        JsonNode importance, JsonNode flexibilityTier) {
    @JsonAnySetter public void unknown(String key, JsonNode value) { throw CommitmentException.invalid("Unknown or server-controlled request field"); }
    static String string(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isString()) throw CommitmentException.invalid("Expected a string or null");
        return node.asString();
    }
    static Long id(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0) throw CommitmentException.invalid("Relationship IDs must be positive integers");
        return node.longValue();
    }
    static Instant deadline(JsonNode node) {
        String value=string(node);
        if (value == null) return null;
        try { return Commitment.normalize(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()); }
        catch (DateTimeParseException ex) { throw CommitmentException.invalid("ownDeadline must be an ISO-8601 date-time with an explicit UTC offset"); }
    }
}
