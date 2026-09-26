package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlotSelectionBoundaryTest {
    static final Instant MIDNIGHT=Instant.parse("2026-09-26T00:00:00Z");
    SchedulingFoundationService.Foundation foundation(long work,long before,long after,CapacityCalculator.Policy policy) {
        var start=MIDNIGHT.minusSeconds(21600);var end=MIDNIGHT.plusSeconds(21600);
        var generator=new CandidateSlotGenerator();
        var slots=generator.generate(List.of(new TimeInterval(start,end)),List.of(),new TimeInterval(start,end),work,policy);
        return new SchedulingFoundationService.Foundation(true,"UTC",start,end,policy,slots,List.of(
                new SchedulingFoundationService.DayCapacity(LocalDate.of(2026,9,25),capacity(before)),
                new SchedulingFoundationService.DayCapacity(LocalDate.of(2026,9,26),capacity(after))));
    }
    CapacityCalculator.Capacity capacity(long budget) { return new CapacityCalculator.Capacity(7200,budget,7200-budget,0,0,0,budget); }
    @Test void midnightCapacityFindsInteriorBoundaryAndPreservesBreakOrigin() {
        var f=foundation(3600,2400,1200,CapacityCalculator.Policy.defaults());
        var slots=SlotSelection.feasible(f,null,List.of());
        assertFalse(slots.isEmpty());
        assertEquals(MIDNIGHT.minusSeconds(2401).plusNanos(1000),slots.get(0).start());
        assertEquals(MIDNIGHT.plusSeconds(1799).plusNanos(1000),slots.get(0).end());
    }
    @Test void budgetEndingAtBreakBoundaryAllowsTheBreakBeforeMidnight() {
        var f=foundation(3600,3000,600,CapacityCalculator.Policy.defaults());
        assertEquals(MIDNIGHT.minusSeconds(3601).plusNanos(1000),SlotSelection.feasible(f,null,List.of()).get(0).start());
    }
    @Test void candidateBoundariesAgreeWithExhaustiveSecondsForCustomBreakPolicies() {
        var calc=new CapacityCalculator();
        for(int continuous:new int[]{7,25,50}) for(int breakMinutes:new int[]{3,10,30}) {
            var policy=new CapacityCalculator.Policy(new java.math.BigDecimal("0.7"),10,continuous,breakMinutes);
            var f=foundation(3600,2400,1200,policy);
            var range=f.candidates().get(0);
            Instant earliest=null;
            for(var t=range.earliestStart();!t.isAfter(range.latestStart());t=t.plusSeconds(1)) {
                long prefix=Duration.between(t,MIDNIGHT).getSeconds();
                long before=calc.deliverableSeconds(Math.min(range.elapsedSeconds(),Math.max(0,prefix)),policy);
                if(before<=2400 && 3600-before<=1200) {earliest=t;break;}
            }
            var choices=SlotSelection.feasible(f,null,List.of());
            Instant selected=choices.isEmpty()?null:choices.get(0).start();
            // The exhaustive oracle samples whole seconds; round the exact microsecond
            // boundary upward before comparing to that oracle.
            if(selected!=null && selected.getNano()!=0) selected=selected.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1);
            assertEquals(earliest,selected,"policy="+policy);
        }
    }
    @Test void interveningDayBreakPhaseCanMakeOnlyAnInteriorStartFeasible() {
        var start=Instant.parse("2026-09-25T15:03:00Z");
        var end=Instant.parse("2026-09-27T04:22:00Z");
        var policy=new CapacityCalculator.Policy(new java.math.BigDecimal(".62"),0,87,50);
        var slots=new CandidateSlotGenerator().generate(List.of(new TimeInterval(start,end)),List.of(),new TimeInterval(start,end),1388*60L,policy);
        var f=new SchedulingFoundationService.Foundation(true,"UTC",start,end,policy,slots,List.of(
                new SchedulingFoundationService.DayCapacity(LocalDate.of(2026,9,25),capacity(348*60)),
                new SchedulingFoundationService.DayCapacity(LocalDate.of(2026,9,26),capacity(53568)),
                new SchedulingFoundationService.DayCapacity(LocalDate.of(2026,9,27),capacity(174*60))));
        var feasible=SlotSelection.feasible(f,null,List.of());
        assertFalse(feasible.isEmpty());
        assertEquals(Instant.parse("2026-09-25T15:39:11.000001Z"),feasible.get(0).start());
    }
    @Test void fractionalRangeFindsEarliestMicrosecondNotApproximateRoot() {
        var start=Instant.parse("2026-09-25T23:19:58.500000Z");
        var end=Instant.parse("2026-09-26T00:30:00.500000Z");
        var f=foundation(3600,2400,1200,CapacityCalculator.Policy.defaults());
        var range=new CandidateSlotGenerator.CandidateSlot(start,Instant.parse("2026-09-25T23:20:00.500000Z"),end,3600,4200);
        var exact=new SchedulingFoundationService.Foundation(true,"UTC",start,end,f.policy(),List.of(range),f.days());
        assertEquals(Instant.parse("2026-09-25T23:19:59.000001Z"),SlotSelection.feasible(exact,null,List.of()).get(0).start());
    }
    @Test void mostRecentBlockWinsIncludingUnknownCategoryAndStableEqualEndTimes() {
        var history=List.of(new SlotSelection.Context(MIDNIGHT.minusSeconds(900),MIDNIGHT.minusSeconds(300),7L,1),
                new SlotSelection.Context(MIDNIGHT.minusSeconds(200),MIDNIGHT.minusSeconds(100),null,2));
        assertNull(SlotSelection.preceding(history,MIDNIGHT));
        var equal=List.of(new SlotSelection.Context(MIDNIGHT.minusSeconds(900),MIDNIGHT,7L,1),
                new SlotSelection.Context(MIDNIGHT.minusSeconds(600),MIDNIGHT,8L,2));
        assertEquals(8L,SlotSelection.preceding(equal,MIDNIGHT));
    }
}
