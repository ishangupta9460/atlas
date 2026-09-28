package com.atlas.backend.recovery;

import com.atlas.backend.commitment.*;
import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.user.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class RecoveryIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired CommitmentService tasks;
    @Autowired MissedBlockDetector detector;
    @Autowired RetrospectiveReportService reports;
    @Autowired RecoveryService recovery;
    @Autowired GoalRiskService risk;
    @Autowired com.atlas.backend.goal.GoalService goals;
    @Autowired com.atlas.backend.execution.ExecutionService execution;
    @Autowired com.atlas.backend.recurringintention.RecurringIntentionResetJob resets;
    @Autowired com.atlas.backend.scheduling.RecurringSchedulingService recurring;
    @Autowired InterruptionService interruptions;
    @Autowired DeferredReviewService deferred;
    @Autowired DeferredReviewTrigger deferredTrigger;
    @Autowired ContextualCompletionEvidence evidence;
    @Autowired com.atlas.backend.recurringintention.RecurringIntentionService recurringDomain;
    @Autowired RecoveryWorkspaceService recoveryWorkspace;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Autowired com.atlas.backend.security.JwtService jwt;
    @org.springframework.test.context.bean.override.mockito.MockitoBean ExecutionClock clock;
    final ObjectMapper mapper=new ObjectMapper();
    long owner;
    final Instant now=Instant.parse("2026-09-21T10:00:00Z");
    @BeforeEach void setup() {
        owner=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();
        when(clock.now()).thenReturn(now);
        db.update("INSERT INTO scheduling_config(user_id,timezone,workable_fraction,buffer_minutes) VALUES(?,'UTC',1,0)",owner);
        for(int day=1;day<=7;day++) db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'08:00:00','18:00:00','working')",owner,day);
    }
    long task() { return tasks.create(owner,mapper.readValue("{\"title\":\"Recover\",\"completionCriterion\":\"Done\",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}",CommitmentRequest.class)).id(); }
    long block(long task, Instant end) {
        db.update("INSERT INTO scheduled_blocks(user_id,commitment_id,start_time,end_time,state,user_moved_flag,placement_reason) VALUES(?,?,?,?,'scheduled',FALSE,'Test placement')",owner,task,utc(end.minusSeconds(1800)),utc(end));
        return db.queryForObject("SELECT MAX(id) FROM scheduled_blocks WHERE user_id=?",Long.class,owner);
    }
    String state(long block) { return db.queryForObject("SELECT state FROM scheduled_blocks WHERE id=?",String.class,block); }
    @Test void detectorUsesInclusiveEndAndDoesNotRepeat() {
        long a=block(task(),now.plusSeconds(1)),b=block(task(),now),c=block(task(),now.minusSeconds(1));
        assertEquals(List.of(c,b),detector.detect(owner)); assertEquals("scheduled",state(a));
        assertTrue(detector.detect(owner).isEmpty());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM events WHERE type='block.unresolved' AND entity_id IN (?,?)",Integer.class,b,c));
    }
    @Test void activeAndActualSessionsAreNeverInferredMissed() {
        long a=block(task(),now),b=block(task(),now);
        db.update("INSERT INTO focus_sessions(scheduled_block_id,state,actual_start,running_since) VALUES(?,'running',?,?)",a,utc(now.minusSeconds(60)),utc(now.minusSeconds(60)));
        db.update("INSERT INTO actual_sessions(scheduled_block_id,actual_start,actual_end,active_millis,user_reported_outcome,completion_pct) VALUES(?,?,?,1,'Done',100)",b,utc(now.minusSeconds(60)),utc(now));
        assertTrue(detector.detect(owner).isEmpty());
    }
    @Test void forgottenCompletionDoesNotFabricateSessionAndReplays() {
        long t=task(),b=block(t,now); detector.detect(owner);
        var report=new RetrospectiveReportService.Report("completed","Worked without timer",BigDecimal.valueOf(100));
        String result=reports.report(owner,b,"retro",report);
        assertEquals(result,reports.report(owner,b,"retro",report));
        assertEquals("completed",tasks.get(owner,t).workState()); assertEquals("completed",state(b));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM actual_sessions WHERE scheduled_block_id=?",Integer.class,b));
        assertThrows(RuntimeException.class,()->reports.report(owner,b,"different",report));
    }
    @Test void partialUsesProgressNotElapsedAndAutonomouslyRecoversOnce() {
        long t=task(),b=block(t,now); detector.detect(owner);
        reports.report(owner,b,"report",new RetrospectiveReportService.Report("partial","Half done",BigDecimal.valueOf(50)));
        var request=new RecoveryService.Request(List.of(new RecoveryService.Item(b,60)));
        String result=recovery.recover(owner,"recover",request);
        var decision=mapper.readValue(result,RecoveryService.Decision.class);
        assertEquals("applied",decision.state()); assertEquals(DecisionTierClassifier.Tier.AUTONOMOUS,decision.proposal().tier());
        assertEquals(1800,Duration.between(decision.blocks().get(0).decision().startTime(),decision.blocks().get(0).decision().endTime()).getSeconds());
        assertEquals("superseded",state(b)); assertEquals(result,recovery.recover(owner,"recover",request));
        assertThrows(RuntimeException.class,()->recovery.recover(owner,"again",request));
    }
    @Test void stickyWorkWaitsForExplicitDecision() {
        long b=block(task(),now); db.update("UPDATE scheduled_blocks SET user_moved_flag=TRUE WHERE id=?",b); detector.detect(owner);
        var d=mapper.readValue(recovery.recover(owner,"recover",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))),RecoveryService.Decision.class);
        assertEquals("pending",d.state()); assertEquals("unresolved",state(b)); assertTrue(d.blocks().isEmpty());
        assertEquals(DecisionTierClassifier.Tier.COLLABORATIVE,d.proposal().tier());
        assertEquals("applied",mapper.readValue(recovery.respond(owner,d.id(),"accept",true),RecoveryService.Decision.class).state());
    }
    @Test void detectorRollsBackWhenEventFails() {
        long b=block(task(),now);
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_recovery CHECK (NOT (entity_id="+b+" AND type='block.unresolved'))");
        try { assertThrows(RuntimeException.class,()->detector.detect(owner)); assertEquals("scheduled",state(b)); }
        finally { db.execute("ALTER TABLE events DROP CONSTRAINT reject_recovery"); }
    }
    @Test void delayedCollaborativeAcceptanceRevalidatesWithoutRejectingTimeAlone() {
        long b=block(task(),now); db.update("UPDATE scheduled_blocks SET user_moved_flag=TRUE WHERE id=?",b);detector.detect(owner);
        var d=mapper.readValue(recovery.recover(owner,"proposal",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))),RecoveryService.Decision.class);
        when(clock.now()).thenReturn(now.plusSeconds(30));
        assertEquals("applied",mapper.readValue(recovery.respond(owner,d.id(),"accept",true),RecoveryService.Decision.class).state());
    }
    @Test void recoveredMissRemainsHistoricalEvidence() {
        long b=block(task(),now); detector.detect(owner);
        recovery.recover(owner,"recover",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30))));
        assertTrue(evidence.read(owner,now).stream().anyMatch(f->f.blockId()==b && !f.completed()));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={0,20,50,80})
    void remainingEstimateUsesProgress(int percent) {
        long b=block(task(),now); detector.detect(owner);
        reports.report(owner,b,"report",new RetrospectiveReportService.Report("partial","Progress",BigDecimal.valueOf(percent)));
        var result=mapper.readValue(recovery.recover(owner,"recover",new RecoveryService.Request(List.of(new RecoveryService.Item(b,50)))),RecoveryService.Decision.class);
        assertEquals(50L*(100-percent)*60/100,Duration.between(result.blocks().get(0).decision().startTime(),result.blocks().get(0).decision().endTime()).getSeconds());
    }
    @Test void progressiveSearchUsesTomorrowThenWeekAndNoSlotRemainsPending() {
        long b=block(task(),now); detector.detect(owner);
        fixed(now,Instant.parse("2026-09-22T00:00:00Z"));
        var first=mapper.readValue(recovery.recover(owner,"tomorrow",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))),RecoveryService.Decision.class);
        assertEquals(LocalDate.of(2026,9,22),first.blocks().get(0).decision().startTime().atZone(ZoneOffset.UTC).toLocalDate());
        long b2=block(task(),now);detector.detect(owner);fixed(now,Instant.parse("2026-09-23T00:00:00Z"));
        var later=mapper.readValue(recovery.recover(owner,"later",new RecoveryService.Request(List.of(new RecoveryService.Item(b2,30)))),RecoveryService.Decision.class);
        assertEquals("pending",later.state());assertEquals(LocalDate.of(2026,9,23),later.proposal().plan().placements().get(0).startTime().atZone(ZoneOffset.UTC).toLocalDate());
        recovery.respond(owner,later.id(),"dismiss",false);
        fixed(now,Instant.parse("2026-09-29T00:00:00Z"));
        var impossible=mapper.readValue(recovery.recover(owner,"none",new RecoveryService.Request(List.of(new RecoveryService.Item(b2,30)))),RecoveryService.Decision.class);
        assertEquals(DecisionTierClassifier.Tier.CRITICAL,impossible.proposal().tier());assertEquals("pending",impossible.state());assertEquals("unresolved",state(b2));
    }
    @Test void overloadProtectsHardConsequenceAndDefersOnlyAfterApproval() {
        fixed(now.plusSeconds(1800),Instant.parse("2026-09-29T00:00:00Z"));
        var items=new ArrayList<RecoveryService.Item>();var tasksCreated=new ArrayList<Long>();
        for(int i=0;i<6;i++) {long t=task();tasksCreated.add(t);items.add(new RecoveryService.Item(block(t,now),30));}
        long winner=tasksCreated.get(5);db.update("UPDATE commitments SET is_hard_consequence=TRUE,own_deadline=? WHERE id=?",utc(now.plusSeconds(1800)),winner);
        detector.detect(owner);Collections.reverse(items);
        var proposal=mapper.readValue(recovery.recover(owner,"overload",new RecoveryService.Request(items)),RecoveryService.Decision.class);
        assertEquals(winner,proposal.proposal().plan().placements().get(0).commitmentId());
        assertEquals(5,proposal.proposal().deferred().size());assertTrue(deferred.review(owner).isEmpty());
        recovery.respond(owner,proposal.id(),"approve",true);assertEquals(5,deferred.review(owner).size());
        assertEquals("ready",tasks.get(owner,winner).workState());
        String restored=deferred.reactivate(owner,"restore",proposal.proposal().deferred());
        assertEquals(restored,deferred.reactivate(owner,"restore",proposal.proposal().deferred()));
    }
    @Test void placementAndDeferralRollBackWithEvents() {
        long b=block(task(),now);detector.detect(owner);
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_placement CHECK (id<="+db.queryForObject("SELECT COALESCE(MAX(id),0) FROM events",Long.class)+" OR type<>'block.generated')");
        try {assertThrows(RuntimeException.class,()->recovery.recover(owner,"fail",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))));
            assertEquals("unresolved",state(b));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM recovery_decisions WHERE user_id=?",Integer.class,owner));}
        finally {db.execute("ALTER TABLE events DROP CONSTRAINT reject_placement");}
        fixed(now,now.plus(Duration.ofDays(8)));
        var d=mapper.readValue(recovery.recover(owner,"defer",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))),RecoveryService.Decision.class);
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_defer CHECK (id<="+db.queryForObject("SELECT COALESCE(MAX(id),0) FROM events",Long.class)+" OR type<>'task.deferred')");
        try {assertThrows(RuntimeException.class,()->recovery.respond(owner,d.id(),"accept",true));assertEquals("ready",tasks.get(owner,db.queryForObject("SELECT commitment_id FROM scheduled_blocks WHERE id=?",Long.class,b)).workState());}
        finally {db.execute("ALTER TABLE events DROP CONSTRAINT reject_defer");}
    }
    @Test void interruptionIsLocalIdempotentAndWaitsForStickyIntent() {
        long b=block(task(),now.plusSeconds(1800));db.update("UPDATE scheduled_blocks SET user_moved_flag=TRUE WHERE id=?",b);
        var request=new InterruptionService.Request(now,now.plusSeconds(3600),List.of(new RecoveryService.Item(b,30)));
        String result=interruptions.interrupt(owner,"interrupt",request);assertEquals(result,interruptions.interrupt(owner,"interrupt",request));
        var d=mapper.readValue(result,InterruptionService.Result.class).recovery();assertEquals("pending",d.state());assertEquals("scheduled",state(b));
        var applied=mapper.readValue(recovery.respond(owner,d.id(),"confirm",true),RecoveryService.Decision.class);
        assertFalse(applied.blocks().get(0).decision().startTime().isBefore(now.plusSeconds(3600)));
    }
    @Test void interruptionRollsBackReservationOnEventFailure() {
        long b=block(task(),now.plusSeconds(1800));
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_interrupt CHECK (id<="+db.queryForObject("SELECT COALESCE(MAX(id),0) FROM events",Long.class)+" OR type<>'block.generated')");
        try {assertThrows(RuntimeException.class,()->interruptions.interrupt(owner,"fail",new InterruptionService.Request(now,now.plusSeconds(3600),List.of(new RecoveryService.Item(b,30)))));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM fixed_commitments WHERE user_id=?",Integer.class,owner));assertEquals("scheduled",state(b));}
        finally {db.execute("ALTER TABLE events DROP CONSTRAINT reject_interrupt");}
    }
    long goal() { return goals.create(owner,new com.atlas.backend.goal.CreateGoalRequest("Goal",null,LocalDate.of(2026,9,21))).id(); }
    long goalTask(long goal) {long t=task();db.update("UPDATE commitments SET goal_id=? WHERE id=?",goal,t);return t;}
    void history(long t,int completed,int total) {
        for(int i=0;i<total;i++) {Instant end=now.minus(Duration.ofDays(i+1));long b=block(t,end);
            db.update("UPDATE scheduled_blocks SET state=? WHERE id=?",i<completed ? "completed" : "unresolved",b);
            if(i<completed) db.update("INSERT INTO actual_sessions(scheduled_block_id,actual_start,actual_end,active_millis,user_reported_outcome,completion_pct) VALUES(?,?,?,1800000,'Done',100)",b,utc(end.minusSeconds(1800)),utc(end));
        }
    }
    @Test void riskUsesContextAndPreservesSilenceUntilUserResponds() {
        long g=goal(),t=goalTask(g);history(t,0,10);
        var input=new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,100,9)));
        String result=risk.evaluate(owner,g,"risk",input);var s=mapper.readValue(result,GoalRiskService.Snapshot.class);
        assertEquals("at_risk",s.calculation().result());assertTrue(s.awaitingResponse());assertEquals("active",goals.get(owner,g).planningState());
        assertEquals(s.id(),risk.get(owner,g).id());assertEquals(result,risk.evaluate(owner,g,"risk",input));
        risk.respond(owner,g,"response",new GoalRiskService.Response("change_method","Try a different approach",null,null));
        assertEquals("at_risk",goals.get(owner,g).planningState());assertTrue(risk.get(owner,g).awaitingResponse());
        risk.respond(owner,g,"pause",new GoalRiskService.Response("defer_pause","I choose to pause",null,null));
        assertEquals("paused",goals.get(owner,g).planningState());assertEquals("active",goals.get(owner,g).lifecycleState());
    }
    @Test void missingHistoryCannotPretendToBeFeasibleAndSnapshotRollsBack() {
        long g=goal(),t=goalTask(g);var input=new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,60,9)));
        assertThrows(com.atlas.backend.execution.ExecutionException.class,()->risk.evaluate(owner,g,"missing",input));assertNull(risk.get(owner,g));
        history(t,1,1);db.execute("ALTER TABLE events ADD CONSTRAINT reject_risk CHECK (id<="+db.queryForObject("SELECT COALESCE(MAX(id),0) FROM events",Long.class)+" OR type<>'goal.risk_evaluated')");
        try {assertThrows(RuntimeException.class,()->risk.evaluate(owner,g,"fail",input));assertNull(risk.get(owner,g));}
        finally {db.execute("ALTER TABLE events DROP CONSTRAINT reject_risk");}
    }
    @Test void concurrentRecoveryTriggersPlaceOnlyOnce() throws Exception {
        long b=block(task(),now);detector.detect(owner);var request=new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->recovery.recover(owner,"same",request));var z=pool.submit(()->recovery.recover(owner,"same",request));assertEquals(a.get(),z.get());}
        finally {pool.shutdownNow();}
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND state='scheduled'",Integer.class,owner));
    }
    @Test void recoveryAndRetrospectiveReportSerializeWithoutDuplicatingRemainder() throws Exception {
        long b=block(task(),now);detector.detect(owner);
        race(()->recovery.recover(owner,"recover",new RecoveryService.Request(List.of(new RecoveryService.Item(b,60)))),
            ()->reports.report(owner,b,"report",new RetrospectiveReportService.Report("completed","Done without timer",BigDecimal.valueOf(100))));
        int scheduled=db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND state='scheduled'",Integer.class,owner);
        assertTrue(scheduled==0 || scheduled==1);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM actual_sessions a JOIN scheduled_blocks b ON b.id=a.scheduled_block_id WHERE b.user_id=?",Integer.class,owner));
        if(state(b).equals("completed")) assertEquals(0,scheduled);
    }
    @Test void detectorAndSessionStartHonorTheSameBoundary() throws Exception {
        long b=block(task(),now.plusSeconds(1));
        race(()->detector.detect(owner),()->execution.transition(owner,b,"start","start",null));
        assertEquals("active",state(b));assertTrue(detector.detect(owner).isEmpty());
    }
    @Test void interruptionAndManualMoveCannotBothConsumeTheOriginalWindow() throws Exception {
        long t=task(),b=block(t,now.plusSeconds(1800));
        race(()->interruptions.interrupt(owner,"interrupt",new InterruptionService.Request(now,now.plusSeconds(3600),List.of(new RecoveryService.Item(b,30)))),
            ()->execution.move(owner,b,"move",new com.atlas.backend.execution.ExecutionController.Placement(t,now.plusSeconds(7200),now.plusSeconds(9000))));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND commitment_id=? AND state='scheduled'",Integer.class,owner,t));
    }
    @Test void riskAndGoalUpdateSerializeTheirSnapshots() throws Exception {
        long g=goal(),t=goalTask(g);history(t,1,1);
        var update=new com.atlas.backend.goal.UpdateGoalRequest();update.setTargetDeadline(LocalDate.of(2026,9,23));
        race(()->risk.evaluate(owner,g,"risk",new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,60,9)))),()->goals.update(owner,g,update));
        var snapshot=risk.get(owner,g);assertNotNull(snapshot);
        assertTrue(Set.of(Instant.parse("2026-09-22T00:00:00Z"),Instant.parse("2026-09-24T00:00:00Z")).contains(snapshot.horizonEnd()));
        assertEquals(LocalDate.of(2026,9,23),goals.get(owner,g).targetDeadline());
    }
    @Test void weeklyResetAndGenerationCannotMultiplyTheTarget() throws Exception {
        db.update("INSERT INTO recurring_intentions(user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(?,'Practice',3,0,'flexible')",owner);
        long ri=db.queryForObject("SELECT id FROM recurring_intentions WHERE user_id=?",Long.class,owner);
        var request=new com.atlas.backend.scheduling.RecurringSchedulingService.Request(List.of(new com.atlas.backend.scheduling.RecurringSchedulingService.Input(ri,30,"medium")));
        race(()->resets.reconcile(owner),()->recurring.generate(owner,"generate",request));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='recurring_intention' AND entity_id=? AND type='recurring_intention.reset'",Integer.class,ri));
    }
    void race(java.util.concurrent.Callable<?> first,java.util.concurrent.Callable<?> second) throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return attempt(first);});var b=pool.submit(()->{gate.await();return attempt(second);});gate.countDown();
            assertTrue(a.get(20,java.util.concurrent.TimeUnit.SECONDS) | b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    boolean attempt(java.util.concurrent.Callable<?> call) throws Exception {
        try {call.call();return true;} catch(com.atlas.backend.execution.ExecutionException e) {assertTrue(e.status()==400 || e.status()==409);return false;}
    }
    @Test void goalLinkedRecurringHistoryFeedsRiskWithoutAnyCommitments() {
        long g=goal();db.update("INSERT INTO recurring_intentions(user_id,goal_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(?,?,'Practice',3,3,'flexible')",owner,g);
        long ri=db.queryForObject("SELECT id FROM recurring_intentions WHERE user_id=?",Long.class,owner);
        db.update("INSERT INTO scheduled_blocks(user_id,recurring_intention_id,start_time,end_time,state,placement_reason) VALUES(?,?,?,?,'unresolved','Missed instance')",owner,ri,utc(now.minus(Duration.ofDays(7)).minusSeconds(1800)),utc(now.minus(Duration.ofDays(7))));
        var result=mapper.readValue(risk.evaluate(owner,g,"risk",new GoalRiskService.Request(List.of(),List.of(new GoalRiskService.RecurringEstimate(ri,90,9)))),GoalRiskService.Snapshot.class);
        assertEquals("at_risk",result.calculation().result());assertEquals(new BigDecimal("90"),result.calculation().remainingMinutes());assertEquals(1,result.contexts().get(0).instances());
    }
    @Test void manualRecurringCompletionAndNextWeekTargetSurviveFirstReconciliation() {
        long ri=recurringDomain.create(owner,new com.atlas.backend.recurringintention.CreateRecurringIntentionRequest(null,"Practice",3,null,"flexible")).id();
        assertEquals(2,recurringDomain.completeInstance(owner,ri).currentWeekRemainingCount());
        recurringDomain.updateTarget(owner,ri,new com.atlas.backend.recurringintention.UpdateRecurringIntentionTargetRequest(5));
        resets.reconcile(owner);
        assertEquals(2,recurringDomain.get(owner,ri).currentWeekRemainingCount());
        var request=new com.atlas.backend.scheduling.RecurringSchedulingService.Request(List.of(new com.atlas.backend.scheduling.RecurringSchedulingService.Input(ri,30,"medium")));
        assertEquals(2,mapper.readTree(recurring.generate(owner,"generate",request)).size());
        when(clock.now()).thenReturn(now.plus(Duration.ofDays(7)));resets.reconcile(owner);
        assertEquals(5,recurringDomain.get(owner,ri).currentWeekRemainingCount());
    }
    @Test void terminalGoalsLeaveRiskHistoryButNoActionablePrompt() {
        long g=goal(),t=goalTask(g);history(t,0,1);
        risk.evaluate(owner,g,"risk",new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,60,9))));
        assertEquals(List.of(g),execution.workspace(owner).riskGoalIds());
        goals.complete(owner,g);assertNotNull(risk.get(owner,g));assertTrue(recoveryWorkspace.read(owner).risks().isEmpty());assertTrue(execution.workspace(owner).riskGoalIds().isEmpty());
    }
    @Test void startingOwnGoalWorkDoesNotRemoveItsRemainingCapacity() {
        long g=goal(),t=goalTask(g);history(t,1,1);
        long b=block(t,now.plusSeconds(1800));fixed(now.plusSeconds(1800),now.plus(Duration.ofDays(1)));
        var input=new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,30,9)));
        var before=mapper.readValue(risk.evaluate(owner,g,"before",input),GoalRiskService.Snapshot.class);
        execution.transition(owner,b,"start","start",null);
        var after=mapper.readValue(risk.evaluate(owner,g,"after",input),GoalRiskService.Snapshot.class);
        assertEquals(0,before.calculation().capacityMinutes().compareTo(after.calculation().capacityMinutes()));
        assertEquals("feasible",after.calculation().result());
    }
    @Test void longHorizonCapacityUsesEachLocalDayBudgetOnce() {
        long g=goal(),t=goalTask(g);history(t,1,1);
        var update=new com.atlas.backend.goal.UpdateGoalRequest();update.setTargetDeadline(LocalDate.of(2026,10,22));goals.update(owner,g,update);
        db.update("UPDATE scheduling_config SET workable_fraction=0.5 WHERE user_id=?",owner);
        var result=mapper.readValue(risk.evaluate(owner,g,"risk",new GoalRiskService.Request(List.of(new GoalRiskService.Estimate(t,100,9)))),GoalRiskService.Snapshot.class);
        assertEquals(0,result.calculation().capacityMinutes().compareTo(BigDecimal.valueOf(32*300L)));
    }
    @Test void recurringMultiWeekLoopResetsWithoutDebtAndKeepsHistory() {
        db.update("INSERT INTO recurring_intentions(user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(?,'Practice',3,3,'flexible')",owner);
        long ri=db.queryForObject("SELECT id FROM recurring_intentions WHERE user_id=?",Long.class,owner);
        var request=new com.atlas.backend.scheduling.RecurringSchedulingService.Request(List.of(new com.atlas.backend.scheduling.RecurringSchedulingService.Input(ri,30,"medium")));
        var first=mapper.readTree(recurring.generate(owner,"week1",request));assertEquals(3,first.size());long b=first.get(0).path("blockId").asLong();
        when(clock.now()).thenReturn(Instant.parse(first.get(0).path("startTime").asText()));
        execution.transition(owner,b,"start","start",null);
        execution.transition(owner,b,"finish","finish",new com.atlas.backend.execution.ExecutionController.Report("Practice complete",BigDecimal.valueOf(100)));
        assertEquals(2,db.queryForObject("SELECT current_week_remaining_count FROM recurring_intentions WHERE id=?",Integer.class,ri));
        when(clock.now()).thenReturn(now.plus(Duration.ofDays(7)));detector.detect(owner);resets.reconcile(owner);assertTrue(resets.reconcile(owner).isEmpty());
        assertEquals(3,db.queryForObject("SELECT current_week_remaining_count FROM recurring_intentions WHERE id=?",Integer.class,ri));
        assertEquals(3,mapper.readTree(recurring.generate(owner,"week2",request)).size());
        assertEquals(1,execution.workspace(owner).history().size());assertEquals(3,db.queryForObject("SELECT target_count_per_week FROM recurring_intentions WHERE id=?",Integer.class,ri));
    }
    void fixed(Instant start,Instant end) {db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(?,'Busy',?,?,'manual')",owner,utc(start),utc(end));}
    @Test void apiAuthenticationTenantIsolationAndIdempotencyHeaders() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var foreignUser=users.save(User.of(UUID.randomUUID()+"@test.example","test"));String token=jwt.generateToken(foreignUser);
        long b=block(task(),now),g=goal();detector.detect(owner);
        var d=mapper.readValue(recovery.recover(owner,"proposal",new RecoveryService.Request(List.of(new RecoveryService.Item(b,30)))),RecoveryService.Decision.class);
        for(String path:List.of("/recovery/workspace","/recovery/deferred","/goals/"+g+"/risk")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        }
        for(var entry:Map.of("/goals/"+g+"/risk","{\"estimates\":[]}","/blocks/"+b+"/report","{\"outcome\":\"completed\",\"report\":\"Done\",\"completionPct\":100}","/recovery/"+d.id()+"/response","{\"accept\":true}").entrySet()) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(entry.getKey()).header("Authorization","Bearer "+token).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(entry.getValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/recovery/workspace").header("Authorization","Bearer "+token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.decisions").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/recovery").header("Authorization","Bearer "+token).contentType("application/json").content("{\"items\":[]}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void recurringRetrospectiveCompletionIsTruthfulIdempotentAndDoesNotCarryDebt() {
        long ri=recurringDomain.create(owner,new com.atlas.backend.recurringintention.CreateRecurringIntentionRequest(null,"Practice",3,null,"flexible")).id();
        db.update("INSERT INTO scheduled_blocks(user_id,recurring_intention_id,start_time,end_time,state,placement_reason) VALUES(?,?,?,?,'scheduled','Practice')",owner,ri,utc(now.minusSeconds(1800)),utc(now));
        long b=db.queryForObject("SELECT id FROM scheduled_blocks WHERE user_id=?",Long.class,owner);
        var input=new RetrospectiveReportService.Report("completed","Practiced without timer",BigDecimal.valueOf(100));
        String result=reports.report(owner,b,"report",input);
        assertEquals(result,reports.report(owner,b,"report",input));
        assertEquals(2,recurringDomain.get(owner,ri).currentWeekRemainingCount());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM actual_sessions WHERE scheduled_block_id=?",Integer.class,b));
        resets.reconcile(owner);assertEquals(2,recurringDomain.get(owner,ri).currentWeekRemainingCount());
        when(clock.now()).thenReturn(now.plus(Duration.ofDays(7)));resets.reconcile(owner);
        assertEquals(3,recurringDomain.get(owner,ri).currentWeekRemainingCount());
    }
    @Test void deferredReviewIsPeriodicBatchedOwnedAndNeverMutatesTheBacklog() {
        var ids=new ArrayList<Long>();
        for(int i=0;i<7;i++) { long id=task();ids.add(id);tasks.recoveryDeferral(owner,id,true,"Overload"); }
        assertTrue(deferredTrigger.propose(owner));assertFalse(deferredTrigger.propose(owner));
        assertEquals(7,recoveryWorkspace.read(owner).deferredReview().count());
        long foreign=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();
        assertNull(deferredTrigger.current(foreign));
        var preview=deferred.preview(owner,new com.atlas.backend.scheduling.SchedulingPipeline.Request(now,now.plusSeconds(3600),List.of(new com.atlas.backend.scheduling.SchedulingPipeline.WorkInput(ids.get(0),30)),null));
        assertFalse(preview.placements().isEmpty());
        assertEquals(7,deferred.review(owner).size());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?",Integer.class,owner));
        when(clock.now()).thenReturn(now.plus(Duration.ofDays(7)));assertTrue(deferredTrigger.propose(owner));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='user' AND entity_id=? AND type='recovery.deferred_review'",Integer.class,owner));
        deferred.reactivate(owner,"reactivate",ids);assertNull(deferredTrigger.current(owner));
    }
    static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
}
