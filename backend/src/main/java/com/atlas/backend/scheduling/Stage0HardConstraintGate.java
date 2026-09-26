package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import java.util.List;

/** Fixed work is never eligible for an autonomous placement, including instructed work. */
public final class Stage0HardConstraintGate {
    private Stage0HardConstraintGate() {}

    public static List<Commitment> movable(List<Commitment> candidates) {
        return candidates.stream().filter(c -> !"fixed".equals(c.getFlexibilityTier())).toList();
    }
}
