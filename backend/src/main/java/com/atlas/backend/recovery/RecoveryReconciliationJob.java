package com.atlas.backend.recovery;

import com.atlas.backend.recurringintention.RecurringIntentionResetJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Each owner is reconciled in the existing service transaction; retries are intrinsic no-ops. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="atlas.recovery.reconciliation-enabled",havingValue="true",matchIfMissing=true)
public class RecoveryReconciliationJob {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(RecoveryReconciliationJob.class);
    private final JdbcTemplate db;
    private final MissedBlockDetector detector;
    private final RecurringIntentionResetJob reset;
    private final DeferredReviewTrigger deferred;
    public RecoveryReconciliationJob(JdbcTemplate db,MissedBlockDetector detector,RecurringIntentionResetJob reset,DeferredReviewTrigger deferred) {
        this.db=db;this.detector=detector;this.reset=reset;this.deferred=deferred;
    }
    @Scheduled(fixedDelayString="${atlas.recovery.reconciliation-delay-ms:60000}",initialDelayString="${atlas.recovery.reconciliation-delay-ms:60000}")
    public void reconcile() {
        for(long owner:db.query("SELECT user_id FROM scheduling_config WHERE timezone IS NOT NULL ORDER BY user_id",(r,n)->r.getLong(1))) {
            try {
                reset.reconcile(owner);detector.detect(owner);deferred.propose(owner);
            } catch (RuntimeException failure) {
                // Exception messages/causes may contain SQL parameters or private tenant data.
                log.error("Recovery reconciliation failed for owner {} (failure type {}); continuing with remaining owners",
                    owner,failure.getClass().getName());
            }
        }
    }
}
