package com.atlas.backend.recovery;

import java.math.*;

/** Capacity coverage, calibrated by observed completion in the applicable context. */
public final class GoalRiskCalculator {
    public record Result(BigDecimal remainingMinutes, BigDecimal capacityMinutes, BigDecimal completionRate,
                         BigDecimal confidence, BigDecimal threshold, String result) {}
    public static Result calculate(BigDecimal remaining, BigDecimal capacity, BigDecimal rate, BigDecimal threshold) {
        if(remaining==null || capacity==null || remaining.signum()<0 || capacity.signum()<0 || threshold==null
            || threshold.signum()<=0 || threshold.compareTo(BigDecimal.ONE)>0
            || (rate!=null && (rate.signum()<0 || rate.compareTo(BigDecimal.ONE)>0))) throw new IllegalArgumentException("Invalid risk inputs");
        BigDecimal confidence;
        if(remaining.signum()==0) confidence=BigDecimal.ONE;
        else if(capacity.signum()==0) confidence=BigDecimal.ZERO;
        else if(rate==null) return new Result(remaining,capacity,null,null,threshold,"insufficient_evidence");
        else confidence=capacity.multiply(rate).divide(remaining,12,RoundingMode.HALF_EVEN).min(BigDecimal.ONE);
        boolean atRisk=remaining.signum()>0 && (capacity.signum()==0 || capacity.multiply(rate).compareTo(remaining.multiply(threshold))<0);
        return new Result(remaining,capacity,rate,confidence,threshold,atRisk ? "at_risk" : "feasible");
    }
}
