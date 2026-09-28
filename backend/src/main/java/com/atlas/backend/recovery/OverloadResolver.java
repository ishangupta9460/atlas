package com.atlas.backend.recovery;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.scheduling.SchedulingPlanner;
import java.util.*;

/** The pipeline already ranks survivors; this class only identifies deferrable losers. */
public final class OverloadResolver {
    public static List<Long> deferred(SchedulingPlanner.Plan plan, Map<Long,Commitment> work) {
        return plan.unplaced().stream().filter(id -> {
            var c=work.get(id);
            return c!=null && c.getWorkState().equals("ready") && Set.of("flexible","optional").contains(c.getFlexibilityTier());
        }).sorted().toList();
    }
}
