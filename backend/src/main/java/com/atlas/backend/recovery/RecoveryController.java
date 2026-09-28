package com.atlas.backend.recovery;

import com.atlas.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class RecoveryController {
    private final MissedBlockDetector detector;
    private final RetrospectiveReportService reports;
    private final RecoveryService recovery;
    private final GoalRiskService risks;
    private final InterruptionService interruptions;
    private final DeferredReviewService deferred;
    private final RecoveryWorkspaceService workspace;
    private final com.atlas.backend.recurringintention.RecurringIntentionResetJob reset;
    private final com.atlas.backend.scheduling.RecurringSchedulingService recurring;
    public RecoveryController(MissedBlockDetector detector, RetrospectiveReportService reports, RecoveryService recovery,
        GoalRiskService risks,InterruptionService interruptions,DeferredReviewService deferred,RecoveryWorkspaceService workspace,
        com.atlas.backend.recurringintention.RecurringIntentionResetJob reset,com.atlas.backend.scheduling.RecurringSchedulingService recurring) {
        this.detector=detector; this.reports=reports; this.recovery=recovery;
        this.risks=risks;this.interruptions=interruptions;this.deferred=deferred;this.workspace=workspace;this.reset=reset;this.recurring=recurring;
    }
    @GetMapping("/recovery/workspace")
    public RecoveryWorkspaceService.Workspace workspace(@AuthenticationPrincipal User user) { return workspace.read(user.getId()); }
    @PostMapping("/recovery/weekly-reset")
    public java.util.List<Long> reset(@AuthenticationPrincipal User user) { return reset.reconcile(user.getId()); }
    @GetMapping("/recovery/deferred")
    public java.util.List<com.atlas.backend.commitment.CommitmentResponse> deferred(@AuthenticationPrincipal User user) { return deferred.review(user.getId()); }
    @PostMapping(value="/recovery/deferred/reactivate",produces="application/json")
    public String reactivate(@AuthenticationPrincipal User user,@RequestHeader("Idempotency-Key") String key,@RequestBody java.util.List<Long> ids) { return deferred.reactivate(user.getId(),key,ids); }
    @PostMapping("/recovery/deferred/preview")
    public com.atlas.backend.scheduling.SchedulingPlanner.Plan preview(@AuthenticationPrincipal User user,@RequestBody com.atlas.backend.scheduling.SchedulingPipeline.Request body) { return deferred.preview(user.getId(),body); }
    @PostMapping(value="/recovery/interruption",produces="application/json")
    public String interrupt(@AuthenticationPrincipal User user,@RequestHeader("Idempotency-Key") String key,@RequestBody InterruptionService.Request body) { return interruptions.interrupt(user.getId(),key,body); }
    @PostMapping(value="/schedule/recurring",produces="application/json")
    public String recurring(@AuthenticationPrincipal User user,@RequestHeader("Idempotency-Key") String key,@RequestBody com.atlas.backend.scheduling.RecurringSchedulingService.Request body) { return recurring.generate(user.getId(),key,body); }
    @GetMapping("/goals/{id}/risk")
    public GoalRiskService.Snapshot risk(@AuthenticationPrincipal User user,@PathVariable Long id) { return risks.get(user.getId(),id); }
    @PostMapping(value="/goals/{id}/risk",produces="application/json")
    public String evaluate(@AuthenticationPrincipal User user,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@RequestBody GoalRiskService.Request body) { return risks.evaluate(user.getId(),id,key,body); }
    @PostMapping(value="/goals/{id}/risk/response",produces="application/json")
    public String riskResponse(@AuthenticationPrincipal User user,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@RequestBody GoalRiskService.Response body) { return risks.respond(user.getId(),id,key,body); }
    @PostMapping("/recovery/detect")
    public java.util.List<Long> detect(@AuthenticationPrincipal User user) { return detector.detect(user.getId()); }
    @PostMapping(value="/blocks/{id}/report",produces="application/json")
    public String report(@AuthenticationPrincipal User user,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,
                         @RequestBody RetrospectiveReportService.Report body) { return reports.report(user.getId(),id,key,body); }
    @PostMapping(value="/recovery",produces="application/json")
    public String recover(@AuthenticationPrincipal User user,@RequestHeader("Idempotency-Key") String key,
                          @RequestBody RecoveryService.Request body) {
        if(body.unavailableStart()!=null || body.unavailableEnd()!=null) throw new IllegalArgumentException("Report unavailable time through the interruption endpoint.");
        return recovery.recover(user.getId(),key,body);
    }
    public record Response(Boolean accept) {}
    @PostMapping(value="/recovery/{id}/response",produces="application/json")
    public String respond(@AuthenticationPrincipal User user,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,
                          @RequestBody Response body) {
        if(body.accept()==null) throw new IllegalArgumentException("Choose whether to accept this proposal.");
        return recovery.respond(user.getId(),id,key,body.accept());
    }
}
