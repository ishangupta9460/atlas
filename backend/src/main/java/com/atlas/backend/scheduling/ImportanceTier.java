package com.atlas.backend.scheduling;

import java.util.Objects;

/**
 * Represents the user importance tier used by Stage 3 of the Atlas Scheduling Engine.
 * Tiers (in descending order of importance): CRITICAL > HIGH > MEDIUM > LOW.
 */
public enum ImportanceTier {
    LOW(1, "low"),
    MEDIUM(2, "medium"),
    HIGH(3, "high"),
    CRITICAL(4, "critical");

    private final int rank;
    private final String wireValue;

    ImportanceTier(int rank, String wireValue) {
        this.rank = rank;
        this.wireValue = wireValue;
    }

    public int getRank() {
        return rank;
    }

    public String getWireValue() {
        return wireValue;
    }

    /**
     * Parses a string representation of importance into an ImportanceTier.
     *
     * @param value raw importance string (e.g. "low", "medium", "high", "critical")
     * @return corresponding ImportanceTier
     * @throws IllegalArgumentException if value is null or unrecognized
     */
    public static ImportanceTier from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Importance tier value cannot be null");
        }
        String normalized = value.trim().toLowerCase();
        for (ImportanceTier tier : values()) {
            if (tier.wireValue.equals(normalized)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Unrecognized importance tier: " + value);
    }
}
