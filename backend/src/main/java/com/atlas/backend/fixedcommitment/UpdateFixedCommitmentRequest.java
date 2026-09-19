package com.atlas.backend.fixedcommitment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;

/** Missing nodes preserve fields; explicit JSON null is distinct and only valid for recurrenceRule. */
public record UpdateFixedCommitmentRequest(JsonNode title, JsonNode startTime, JsonNode endTime, JsonNode recurrenceRule) {
    @JsonAnySetter
    public void rejectUnknown(String name, JsonNode value) {
        throw new InvalidFixedCommitmentException("Unknown or server-controlled request field");
    }
}
