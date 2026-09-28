package com.atlas.backend.recovery;

import com.atlas.backend.recurringintention.RecurringIntentionResetJob;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecoveryReconciliationJobTest {
    @ParameterizedTest
    @ValueSource(strings={"reset","detect","deferred"})
    void ownerFailureIsLoggedWithoutPrivateDetailsAndLaterOwnersStillRun(String stage) {
        var db=mock(JdbcTemplate.class);
        var reset=mock(RecurringIntentionResetJob.class);
        var detector=mock(MissedBlockDetector.class);
        var deferred=mock(DeferredReviewTrigger.class);
        when(db.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Long>>any())).thenReturn(List.of(1L,2L,3L));
        var failure=new IllegalStateException("private tenant payload");
        switch(stage) {
            case "reset" -> when(reset.reconcile(2L)).thenThrow(failure);
            case "detect" -> when(detector.detect(2L)).thenThrow(failure);
            default -> when(deferred.propose(2L)).thenThrow(failure);
        }
        var logger=(Logger)LoggerFactory.getLogger(RecoveryReconciliationJob.class);
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            var job=new RecoveryReconciliationJob(db,detector,reset,deferred);
            assertDoesNotThrow(job::reconcile);
            for(long owner:List.of(1L,3L)) {
                verify(reset).reconcile(owner);verify(detector).detect(owner);verify(deferred).propose(owner);
            }
            assertEquals(1,appender.list.size());
            var event=appender.list.get(0);
            assertTrue(event.getFormattedMessage().contains("owner 2"));
            assertTrue(event.getFormattedMessage().contains("IllegalStateException"));
            assertFalse(event.getFormattedMessage().contains("private tenant payload"));
            assertNull(event.getThrowableProxy());
            assertDoesNotThrow(job::reconcile);
            verify(deferred,times(2)).propose(3L);
        } finally { logger.detachAppender(appender);appender.stop(); }
    }
}
