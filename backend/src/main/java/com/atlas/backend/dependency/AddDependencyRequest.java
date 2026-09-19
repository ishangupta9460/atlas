package com.atlas.backend.dependency;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;

public record AddDependencyRequest(JsonNode blockingCommitmentId) {
    @JsonAnySetter public void unknown(String key, JsonNode value) {
        throw DependencyException.invalid("Unknown or server-controlled request field");
    }
    Long blockingId() {
        if (blockingCommitmentId == null || blockingCommitmentId.isNull()) {
            throw DependencyException.invalid("blockingCommitmentId is required");
        }
        if (!blockingCommitmentId.isIntegralNumber() || !blockingCommitmentId.canConvertToLong()
                || blockingCommitmentId.longValue() <= 0) {
            throw DependencyException.invalid("Relationship IDs must be positive integers");
        }
        return blockingCommitmentId.longValue();
    }
}
