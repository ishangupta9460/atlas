package com.atlas.backend.scheduling;

/** DEC-0017 temporary, equal-weight Problem B policy. No inferred evidence. */
public final class CandidateSlotScorer {
    private CandidateSlotScorer() {}
    public record Score(double timeOfDay, double energy, double continuity, double fragmentation) {
        public Score {
            for (double value : new double[]{timeOfDay, energy, continuity, fragmentation})
                if (!Double.isFinite(value) || value < 0 || value > 1)
                    throw new IllegalArgumentException("Slot dimensions must be in [0,1]");
        }
        public double total() { return (timeOfDay + energy + continuity + fragmentation) / 4; }
    }
    public static Score score(Double confirmedTimeOfDay, Double confirmedEnergy, boolean continuity,
                              long leftoverSeconds, long workSeconds) {
        if (leftoverSeconds < 0 || workSeconds <= 0) throw new IllegalArgumentException("Invalid gap or work duration");
        double penalty = leftoverSeconds >= workSeconds ? 0 : (double) leftoverSeconds / workSeconds;
        return new Score(confirmedTimeOfDay == null ? 0 : confirmedTimeOfDay,
                confirmedEnergy == null ? 0 : confirmedEnergy, continuity ? 1 : 0, 1 - penalty);
    }
}
