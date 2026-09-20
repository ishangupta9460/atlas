package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import java.util.Objects;

/**
 * Stage 2 hard-consequence gate from the deterministic scheduling hierarchy.
 *
 * <p>The gate consumes only the persisted, validated hard-consequence flag.
 * A deadline by itself never qualifies a commitment, and this class performs
 * no AI work or persistence.
 */
public final class Stage2HardConsequenceGate {

    private Stage2HardConsequenceGate() {
    }

    public static boolean qualifies(Commitment commitment) {
        Objects.requireNonNull(commitment, "commitment must not be null");
        return commitment.isHardConsequence();
    }
}
