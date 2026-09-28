package com.atlas.backend.recovery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Observations only. No preference writes or learned scheduling defaults. */
public final class PatternDetector {
    public record Evidence(LocalDate week, boolean completed) {}
    public record Policy(int minimumWeeks,int minimumInstances,BigDecimal missedRatio) {
        public Policy {
            if(minimumWeeks<2 || minimumInstances<1 || missedRatio==null || missedRatio.signum()<=0 || missedRatio.compareTo(BigDecimal.ONE)>0)
                throw new IllegalArgumentException("Pattern evidence must span multiple weeks and meaningful frequency.");
        }
    }
    public record Observation(int weeks,int instances,int missed,String message) {}
    public static Optional<Observation> detect(List<Evidence> evidence,Policy policy) {
        Objects.requireNonNull(policy, "An explicit pattern policy is required");
        int weeks=(int)evidence.stream().map(e -> Objects.requireNonNull(e.week()).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))).distinct().count();
        int missed=(int)evidence.stream().filter(e->!e.completed()).count();
        if(weeks<policy.minimumWeeks() || evidence.size()<policy.minimumInstances()
            || BigDecimal.valueOf(missed).compareTo(policy.missedRatio().multiply(BigDecimal.valueOf(evidence.size())))<0) return Optional.empty();
        return Optional.of(new Observation(weeks,evidence.size(),missed,
            "I've noticed these blocks are completed less often: "+missed+" of "+evidence.size()+" instances across "+weeks+" weeks did not record completion."));
    }
}
