package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;

/** Ordinal resistance to deferral, not a weighted importance score. */
public final class Stage6FlexibilityTier {
    private Stage6FlexibilityTier() {}
    public static int resistance(Commitment c) {
        return switch (c.getFlexibilityTier()) {
            case "fixed" -> 3;
            case "protected" -> 2;
            case "flexible" -> 1;
            case "optional" -> 0;
            default -> throw new IllegalArgumentException("Invalid flexibility tier");
        };
    }
    public static int compare(Commitment first, Commitment second) {
        // New placements move zero existing blocks. Defer the less resistant work.
        return Integer.compare(resistance(second), resistance(first));
    }
}
