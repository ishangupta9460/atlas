package com.atlas.backend.recovery;

import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecoveryWorkspaceService {
    public record Recurring(long id,String title,int target,int remaining,Long goalId,String flexibilityTier) {}
    public record GoalChoice(long id,String title) {}
    public record Workspace(List<RecoveryService.Decision> decisions,List<GoalRiskService.Snapshot> risks,List<Recurring> recurring,List<Long> reportedBlocks,List<Long> claimedBlocks,String patternStatus,PatternObservationService.Result patterns,List<GoalChoice> goals,DeferredReviewTrigger.Suggestion deferredReview) {}
    private final JdbcTemplate db;
    private final GoalRiskService risks;
    private final PatternObservationService patterns;
    private final DeferredReviewTrigger deferred;
    private final ObjectMapper mapper=new ObjectMapper();
    public RecoveryWorkspaceService(JdbcTemplate db,GoalRiskService risks,PatternObservationService patterns,DeferredReviewTrigger deferred) { this.db=db;this.risks=risks;this.patterns=patterns;this.deferred=deferred; }
    @Transactional(readOnly=true)
    public Workspace read(Long owner) {
        var decisions=db.query("SELECT id,state,proposal_json FROM recovery_decisions WHERE user_id=? AND state='pending' ORDER BY id",
            (r,n)->new RecoveryService.Decision(r.getLong(1),r.getString(2),mapper.readValue(r.getString(3),RecoveryService.Proposal.class),List.of()),owner);
        var goals=db.query("SELECT g.id FROM goals g JOIN goal_risk_reviews r ON r.goal_id=g.id WHERE g.user_id=? AND g.lifecycle_state='active' AND g.planning_state<>'paused' AND (r.awaiting_response=TRUE OR g.planning_state='at_risk') ORDER BY g.id",(r,n)->r.getLong(1),owner);
        var recurring=db.query("SELECT id,title,target_count_per_week,current_week_remaining_count,goal_id,flexibility_tier FROM recurring_intentions WHERE user_id=? ORDER BY id",
            (r,n)->new Recurring(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4),r.getObject(5,Long.class),r.getString(6)),owner);
        var reported=db.query("SELECT DISTINCT b.id FROM scheduled_blocks b JOIN events e ON e.entity_type='scheduled_block' AND e.entity_id=b.id AND e.type='block.reported' WHERE b.user_id=? ORDER BY b.id",(r,n)->r.getLong(1),owner);
        var claims=db.query("SELECT b.id FROM scheduled_blocks b JOIN recovery_block_claims c ON c.scheduled_block_id=b.id WHERE b.user_id=? ORDER BY b.id",(r,n)->r.getLong(1),owner);
        var choices=db.query("SELECT id,title FROM goals WHERE user_id=? AND lifecycle_state='active' ORDER BY id",(r,n)->new GoalChoice(r.getLong(1),r.getString(2)),owner);
        return new Workspace(decisions,goals.stream().map(g->risks.get(owner,g)).toList(),recurring,reported,claims,"Production observations await an approved evidence span, sample count and ratio.",patterns.observations(owner),choices,deferred.current(owner));
    }
}
