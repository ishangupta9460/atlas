package com.atlas.backend.fixedcommitment;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Strict types are local to this API; other domains keep their existing Jackson behavior. */
final class FixedCommitmentInput {
    private FixedCommitmentInput() { }

    static String title(JsonNode value) {
        if (value == null || !value.isTextual()) throw new InvalidFixedCommitmentException("title must be a string");
        return value.textValue();
    }

    static Instant time(JsonNode value, String field) {
        if (value == null || !value.isTextual()) throw new InvalidFixedCommitmentException(field + " must be an offset timestamp");
        try {
            return OffsetDateTime.parse(value.textValue()).toInstant();
        } catch (DateTimeParseException ex) {
            throw new InvalidFixedCommitmentException(field + " must be an ISO-8601 timestamp with an explicit offset");
        }
    }

    static void recurrence(JsonNode value) {
        if (value != null && !value.isNull()) {
            throw new InvalidFixedCommitmentException("recurrenceRule must be null; recurrence is not supported in DOM-006");
        }
    }
}
