package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import java.util.List;
import java.util.Optional;

/** A request-scoped instruction selects only from already eligible, feasible movable work. */
public final class Stage1ExplicitInstruction {
    private Stage1ExplicitInstruction() {}

    public static Optional<Commitment> select(List<Commitment> feasibleMovable, Long instructedId) {
        if (instructedId == null) return Optional.empty();
        return Stage0HardConstraintGate.movable(feasibleMovable).stream()
                .filter(c -> instructedId.equals(c.getId())).findFirst();
    }
}
