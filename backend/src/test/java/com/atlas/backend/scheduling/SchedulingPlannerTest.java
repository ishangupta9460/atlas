package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.dependency.CommitmentDependency;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingPlannerTest {
    static final Instant START = Instant.parse("2026-09-25T09:00:00Z");
    static Commitment work(long id) {
        try {
            var constructor = Commitment.class.getDeclaredConstructor(); constructor.setAccessible(true);
            var c = constructor.newInstance();
            field(c,"id",id); field(c,"userId",1L); field(c,"importance","medium");
            field(c,"flexibilityTier","flexible"); field(c,"workState","ready");
            field(c,"createdAt",START.minusSeconds(1000-id));
            return c;
        } catch (Exception e) { throw new AssertionError(e); }
    }
    static void field(Object c, String name, Object value) { ReflectionTestUtils.setField(c,name,value); }
    static CommitmentDependency edge(long a,long b) {
        try {
            var ctor = CommitmentDependency.class.getDeclaredConstructor(); ctor.setAccessible(true);
            var e = ctor.newInstance(); field(e,"blockingCommitmentId",a); field(e,"blockedCommitmentId",b); return e;
        } catch(Exception e) { throw new AssertionError(e); }
    }
    static SchedulingFoundationService.Snapshot calendar(int hours, List<TimeInterval> busy) {
        var h = new WorkingHours("UTC", List.of(new WorkingHours.Window(5,LocalTime.of(9,0),LocalTime.of(17,0),"working")));
        return new SchedulingFoundationService.Snapshot(h,START,START.plusSeconds(hours*3600L),
                CapacityCalculator.Policy.defaults(),busy,List.of(),
                new WeeklyAvailability.Expanded(List.of(new TimeInterval(START,START.plusSeconds(hours*3600L))),List.of()));
    }
    SchedulingPlanner.Plan plan(List<Commitment> work,List<CommitmentDependency> edges,Long instruction,
                                SchedulingFoundationService.Snapshot calendar,List<SlotSelection.Context> context) {
        var minutes = new HashMap<Long,Integer>(); work.forEach(c -> minutes.put(c.getId(),30));
        return new SchedulingPlanner().plan(work,edges,Set.of(),Set.of(),minutes,instruction,calendar,context);
    }
    @Test void emptyAllFixedMixedAndAllMovable() {
        var a=work(1); var b=work(2);
        assertTrue(Stage0HardConstraintGate.movable(List.of()).isEmpty());
        assertEquals(2,Stage0HardConstraintGate.movable(List.of(a,b)).size());
        field(a,"flexibilityTier","fixed");
        assertEquals(List.of(b),Stage0HardConstraintGate.movable(List.of(a,b)));
        field(b,"flexibilityTier","fixed");
        assertTrue(plan(List.of(a,b),List.of(),1L,calendar(8,List.of()),List.of()).placements().isEmpty());
    }
    @Test void explicitInstructionWinsButCannotViolateFixedOrPackedCalendar() {
        var a=work(1); var b=work(2); field(a,"hardConsequence",true);
        assertEquals(1L,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),List.of()).placements().get(0).commitmentId());
        var instructed=plan(List.of(a,b),List.of(),2L,calendar(8,List.of()),List.of());
        assertEquals(2L,instructed.placements().get(0).commitmentId());
        assertTrue(instructed.placements().get(0).placementReason().contains("explicitly"));
        assertTrue(plan(List.of(a,b),List.of(),2L,calendar(8,List.of(new TimeInterval(START,START.plusSeconds(28800)))),List.of()).placements().isEmpty());
        field(b,"flexibilityTier","fixed");
        assertTrue(Stage1ExplicitInstruction.select(List.of(a,b),2L).isEmpty());
    }
    @Test void unblockingFirstExcludesTerminalButTraversesThroughThem() {
        var a=work(1); var b=work(2); var c=work(3); var d=work(4);
        field(b,"workState","completed"); field(c,"workState","cancelled");
        var graph=List.of(edge(1,2),edge(2,3),edge(3,4));
        var snapshot=Map.of(1L,a,2L,b,3L,c,4L,d);
        assertEquals(1,Stage5DependencyTier.evaluate(a,graph,snapshot).downstreamCount());
        assertEquals(0,Stage5DependencyTier.evaluate(d,graph,snapshot).downstreamCount());
        field(b,"workState","ready"); field(c,"workState","ready");
        assertEquals(3,Stage5DependencyTier.evaluate(a,graph,snapshot).downstreamCount());
    }
    @Test void countsThenCompletionAndExactTies() {
        var a=new Stage5DependencyTier.Signal(1,new BigDecimal("1"),false,false);
        var b=new Stage5DependencyTier.Signal(0,new BigDecimal("99"),false,false);
        assertTrue(Stage5DependencyTier.compare(a,b)<0);
        assertTrue(Stage5DependencyTier.compare(new Stage5DependencyTier.Signal(0,new BigDecimal("100"),false,false),b)<0);
        assertEquals(0,Stage5DependencyTier.compare(b,b));
    }
    @Test void branchingDepthCountAndCyclesRemainBounded() {
        var graph=new ArrayList<CommitmentDependency>(); var snapshot=new HashMap<Long,Commitment>();
        for(long i=1;i<=80;i++) snapshot.put(i,work(i));
        for(long i=2;i<=80;i++) graph.add(edge(1,i));
        var wide=Stage5DependencyTier.evaluate(snapshot.get(1L),graph,snapshot);
        assertEquals(64,wide.downstreamCount()); assertTrue(wide.truncated());
        graph.clear(); for(long i=1;i<80;i++) graph.add(edge(i,i+1));
        var deep=Stage5DependencyTier.evaluate(snapshot.get(1L),graph,snapshot);
        assertEquals(8,deep.downstreamCount()); assertTrue(deep.truncated());
        graph.clear(); graph.addAll(List.of(edge(1,2),edge(1,3),edge(2,4),edge(3,4)));
        assertEquals(3,Stage5DependencyTier.evaluate(snapshot.get(1L),graph,snapshot).downstreamCount());
        graph.add(edge(4,1));
        assertTrue(Stage5DependencyTier.evaluate(snapshot.get(1L),graph,snapshot).cycleDetected());
    }
    @Test void categoryOnlyAndNoContextFallsThrough() {
        var a=work(1); var b=work(2); field(b,"categoryId",7L);
        assertTrue(Stage8CategoryContinuity.compare(a,b,7L)>0);
        assertEquals(0,Stage8CategoryContinuity.compare(a,b,null));
        assertEquals(0,Stage8CategoryContinuity.compare(a,b,8L));
        var context=List.of(new SlotSelection.Context(START.minusSeconds(3600),START.minusSeconds(600),7L,1));
        assertEquals(2L,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),context).placements().get(0).commitmentId());
        field(a,"importance","critical");
        assertEquals(1L,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),context).placements().get(0).commitmentId());
    }
    @Test void flexibilityAndHigherStagesHavePriority() {
        var a=work(1);var b=work(2);field(a,"flexibilityTier","optional");field(b,"flexibilityTier","protected");
        assertTrue(Stage6FlexibilityTier.compare(a,b)>0);
        assertEquals(2L,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),List.of()).placements().get(0).commitmentId());
        field(a,"importance","critical");
        assertEquals(1L,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),List.of()).placements().get(0).commitmentId());
    }
    WorkRanking.Candidate ranked(Commitment c,int count) { return new WorkRanking.Candidate(c,new Stage5DependencyTier.Signal(count,c.getCurrentCompletionPct(),false,false),false); }
    @Test void allFiveTieRulesAndImportantTieSurfacing() {
        var a=work(1);var b=work(2);
        field(b,"currentCompletionPct",new BigDecimal("40"));
        assertTrue(WorkRanking.tieBreak(ranked(a,0),ranked(b,0)).order()>0);
        field(a,"currentCompletionPct",new BigDecimal("40"));field(a,"ownDeadline",START.plusSeconds(500));
        assertTrue(WorkRanking.tieBreak(ranked(a,0),ranked(b,0)).order()<0);
        field(b,"ownDeadline",a.getOwnDeadline());field(b,"flexibilityTier","protected");
        assertTrue(WorkRanking.tieBreak(ranked(a,0),ranked(b,0)).order()>0);
        field(a,"flexibilityTier","protected");
        assertTrue(WorkRanking.tieBreak(ranked(a,2),ranked(b,1)).order()<0);
        assertTrue(WorkRanking.tieBreak(ranked(a,2),ranked(b,2)).order()<0);
        field(a,"importance","high");field(b,"importance","high");
        assertEquals(1,plan(List.of(a,b),List.of(),null,calendar(8,List.of()),List.of()).importantTies().size());
        field(b,"createdAt",a.getCreatedAt());
        assertTrue(WorkRanking.tieBreak(ranked(a,0),ranked(b,0)).order()<0);
    }
    @Test void scoringUsesApprovedFormulaAndUnknownEvidenceIsZero() {
        var s=CandidateSlotScorer.score(null,null,true,900,1800);
        assertEquals(0,s.timeOfDay());assertEquals(0,s.energy());assertEquals(1,s.continuity());assertEquals(.5,s.fragmentation());assertEquals(.375,s.total());
        assertEquals(1,CandidateSlotScorer.score(null,null,false,0,1800).fragmentation());
        assertEquals(1,CandidateSlotScorer.score(null,null,false,1800,1800).fragmentation());
        assertThrows(IllegalArgumentException.class,()->CandidateSlotScorer.score(2.0,null,false,0,1800));
        var early=new SlotSelection.Choice(START,START.plusSeconds(1800),CandidateSlotScorer.score(null,null,false,0,1800));
        var late=new SlotSelection.Choice(START.plusSeconds(3600),START.plusSeconds(5400),CandidateSlotScorer.score(1.0,1.0,false,0,1800));
        assertEquals(late,SlotSelection.best(List.of(early,late)));
    }
    @Test void exactCapacityBoundaryNoSlotAndAlreadyScheduled() {
        var a=work(1);var b=work(2);
        // One hour at 70% permits one 30-minute item, not two.
        assertEquals(1,plan(List.of(a,b),List.of(),null,calendar(1,List.of()),List.of()).placements().size());
        var cal=calendar(1,List.of());
        var exact=new SchedulingPlanner().plan(List.of(a),List.of(),Set.of(),Set.of(),Map.of(1L,42),null,cal,List.of());
        assertEquals(1,exact.placements().size());
        assertTrue(new SchedulingPlanner().plan(List.of(a),List.of(),Set.of(),Set.of(),Map.of(1L,43),null,cal,List.of()).placements().isEmpty());
        assertTrue(new SchedulingPlanner().plan(List.of(a),List.of(),Set.of(),Set.of(1L),Map.of(1L,30),null,cal,List.of()).placements().isEmpty());
    }
    @Test void hundredShuffledRunsReturnIdenticalPlanAndReasons() {
        var items=new ArrayList<Commitment>();for(int i=1;i<=12;i++) items.add(work(i));
        var graph=new ArrayList<>(List.of(edge(1,7),edge(1,8),edge(2,9),edge(3,10)));
        var expected=plan(items,graph,null,calendar(8,List.of()),List.of());
        var random=new Random(42);
        for(int i=0;i<100;i++) { Collections.shuffle(items,random);Collections.shuffle(graph,random);assertEquals(expected,plan(items,graph,null,calendar(8,List.of()),List.of())); }
    }
    @Test void unrelatedShortLowPriorityWorkCannotChangeLongContendersContinuity() {
        var a=work(1);var b=work(2);var shortWork=work(3);
        field(a,"importance","high");field(b,"importance","high");field(shortWork,"importance","low");
        field(a,"categoryId",7L);field(b,"categoryId",8L);
        var cal=calendar(8,List.of(new TimeInterval(START.plusSeconds(3600),START.plusSeconds(14400))));
        var context=List.of(new SlotSelection.Context(START.minusSeconds(1800),START.minusSeconds(600),7L,10),
                new SlotSelection.Context(START.plusSeconds(3600),START.plusSeconds(14400),8L,11));
        var planner=new SchedulingPlanner();
        var without=planner.plan(List.of(a,b),List.of(),Set.of(),Set.of(),Map.of(1L,90,2L,90),null,cal,context);
        var with=planner.plan(List.of(a,b,shortWork),List.of(),Set.of(),Set.of(),Map.of(1L,90,2L,90,3L,30),null,cal,context);
        assertEquals(2L,without.placements().get(0).commitmentId());
        assertEquals(without.placements().get(0),with.placements().get(0));
    }
    @Test void fiftyCandidatePerformance() {
        var items=new ArrayList<Commitment>();for(int i=1;i<=50;i++) items.add(work(i));
        assertTimeout(Duration.ofSeconds(2),()->plan(items,List.of(),null,calendar(8,List.of()),List.of()));
    }
}
