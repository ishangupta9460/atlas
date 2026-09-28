package com.atlas.backend.recovery;

import java.time.LocalDate;

/** Classification depends on the complete proposed change, never an individual winning placement. */
public final class DecisionTierClassifier {
    public enum Tier { AUTONOMOUS, COLLABORATIVE, CRITICAL }
    public record Touches(int items, boolean onlyFlexible, boolean fixedOrProtected, boolean sticky,
                          boolean important, boolean atRisk, boolean importantTie, boolean pattern,
                          boolean firstRisk, boolean pauseOrAbandon, boolean noValidSchedule,
                          boolean hardConflict, LocalDate today, LocalDate latestPlacement) {}
    public static Tier classify(Touches t) {
        if(t.fixedOrProtected() || t.firstRisk() || t.pauseOrAbandon() || t.noValidSchedule() || t.hardConflict()) return Tier.CRITICAL;
        if(t.items()!=1 || !t.onlyFlexible() || t.sticky() || t.important() || t.atRisk() || t.importantTie() || t.pattern()
            || t.latestPlacement()==null || t.latestPlacement().isAfter(t.today().plusDays(1))) return Tier.COLLABORATIVE;
        return Tier.AUTONOMOUS;
    }
}
