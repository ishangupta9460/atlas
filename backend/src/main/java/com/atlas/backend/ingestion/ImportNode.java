package com.atlas.backend.ingestion;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;

/** Stable document-order IDs and parent references survive review; selection is explicit. */
public record ImportNode(int id,Integer parentId,String type,String title,String text,boolean included,
                         String completionCriterion,String importance,String flexibilityTier,
                         String resourceType,String reference,Long resourceId,
                         String startTime,String endTime,String warning) {
    @JsonAnySetter public void unknown(String key,JsonNode value){throw new IllegalArgumentException("Unknown review field");}
}
