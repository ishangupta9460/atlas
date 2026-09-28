package com.atlas.backend.recovery;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRulesTest {
    @ParameterizedTest @CsvSource({"79,at_risk","80,feasible","81,feasible","100,feasible"})
    void launchBoundary(int capacity,String expected) {
        assertEquals(expected,GoalRiskCalculator.calculate(new BigDecimal("100"),BigDecimal.valueOf(capacity),BigDecimal.ONE,new BigDecimal("0.80")).result());
    }
    @Test void capacityIsAdjustedByContextualRate() {
        assertEquals("at_risk",GoalRiskCalculator.calculate(new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("0.58"),new BigDecimal("0.80")).result());
        assertEquals("feasible",GoalRiskCalculator.calculate(new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("0.92"),new BigDecimal("0.80")).result());
    }
    @Test void zeroCapacityAndZeroWorkAreDefinedWithoutFakeHistory() {
        assertEquals("feasible",GoalRiskCalculator.calculate(BigDecimal.ZERO,BigDecimal.ZERO,null,new BigDecimal("0.80")).result());
        assertEquals("at_risk",GoalRiskCalculator.calculate(BigDecimal.ONE,BigDecimal.ZERO,null,new BigDecimal("0.80")).result());
    }
    @Test void comparisonDoesNotRoundAnUnderThresholdValueUp() {
        assertEquals("at_risk",GoalRiskCalculator.calculate(BigDecimal.ONE,new BigDecimal("0.79999999999999"),BigDecimal.ONE,new BigDecimal("0.80")).result());
    }
    @Test void explicitPatternPolicyHonorsFrequencyAcrossNormalizedWeeks() {
        var policy=new PatternDetector.Policy(2,10,new BigDecimal("0.80"));
        var evidence=new ArrayList<PatternDetector.Evidence>();
        for(int i=0;i<10;i++) evidence.add(new PatternDetector.Evidence(LocalDate.of(2026,9,1).plusDays(i*3L),i>=8));
        assertTrue(PatternDetector.detect(evidence,policy).isPresent());
        Collections.reverse(evidence); assertTrue(PatternDetector.detect(evidence,policy).isPresent());
        evidence.set(0,new PatternDetector.Evidence(evidence.get(0).week(),false));
        assertTrue(PatternDetector.detect(evidence,policy).get().message().startsWith("I've noticed"));
    }
    @Test void threeMissesInOneWeekCannotBeAPatternEvenWithPermissiveSamples() {
        var evidence=List.of(new PatternDetector.Evidence(LocalDate.of(2026,9,21),false),new PatternDetector.Evidence(LocalDate.of(2026,9,22),false),new PatternDetector.Evidence(LocalDate.of(2026,9,23),false));
        assertTrue(PatternDetector.detect(evidence,new PatternDetector.Policy(2,1,new BigDecimal("0.5"))).isEmpty());
        assertThrows(NullPointerException.class,()->PatternDetector.detect(evidence,null));
    }
    @Test void progressiveWindowsRespectLocalMidnightAndDst() {
        ZoneId zone=ZoneId.of("America/New_York");
        var windows=ProgressiveSlotSearcher.windows(Instant.parse("2026-10-31T12:00:00Z"),zone);
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"),windows.get(0).end());
        assertEquals(25,Duration.between(windows.get(1).start(),windows.get(1).end()).toHours());
    }
    @ParameterizedTest @CsvSource({"1,true,false,false,false,false,false,false,false,false,false,false,1,AUTONOMOUS", "2,true,false,false,false,false,false,false,false,false,false,false,1,COLLABORATIVE", "1,true,false,true,false,false,false,false,false,false,false,false,1,COLLABORATIVE", "1,true,false,false,true,false,false,false,false,false,false,false,1,COLLABORATIVE", "1,true,false,false,false,true,false,false,false,false,false,false,1,COLLABORATIVE", "1,true,false,false,false,false,true,false,false,false,false,false,1,COLLABORATIVE", "1,true,false,false,false,false,false,true,false,false,false,false,1,COLLABORATIVE", "1,true,false,false,false,false,false,false,false,false,false,false,3,COLLABORATIVE", "1,true,true,false,false,false,false,false,false,false,false,false,1,CRITICAL", "1,true,false,false,false,false,false,false,true,false,false,false,1,CRITICAL", "1,true,false,false,false,false,false,false,false,true,false,false,1,CRITICAL", "1,true,false,false,false,false,false,false,false,false,true,false,1,CRITICAL", "1,true,false,false,false,false,false,false,false,false,false,true,1,CRITICAL"})
    void tierBoundaries(int items,boolean flexible,boolean protectedWork,boolean sticky,boolean important,boolean risk,boolean tie,boolean pattern,boolean firstRisk,boolean pause,boolean impossible,boolean conflict,int days,String expected) {
        var today=LocalDate.of(2026,9,21);
        assertEquals(expected,DecisionTierClassifier.classify(new DecisionTierClassifier.Touches(items,flexible,protectedWork,sticky,important,risk,tie,pattern,firstRisk,pause,impossible,conflict,today,today.plusDays(days))).name());
    }
}
