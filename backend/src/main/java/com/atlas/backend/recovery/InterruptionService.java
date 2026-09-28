package com.atlas.backend.recovery;

import com.atlas.backend.execution.*;
import com.atlas.backend.fixedcommitment.*;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class InterruptionService {
    public record Request(Instant startTime,Instant endTime,List<RecoveryService.Item> items) {}
    public record Result(long reservationId,List<Long> affected,List<Long> needsUserAction,RecoveryService.Decision recovery) {}
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final FixedCommitmentService fixed;
    private final RecoveryService recovery;
    private final ExecutionIdempotency replay;
    private final ExecutionClock clock;
    private final ObjectMapper mapper=new ObjectMapper();
    public InterruptionService(JdbcTemplate db,SchedulingConfigRepository config,FixedCommitmentService fixed,
                               RecoveryService recovery,ExecutionIdempotency replay,ExecutionClock clock) {
        this.db=db; this.config=config; this.fixed=fixed; this.recovery=recovery; this.replay=replay; this.clock=clock;
    }
    @Transactional
    public String interrupt(Long owner,String key,Request r) {
        if(r==null || r.startTime()==null || r.endTime()==null || !r.endTime().isAfter(r.startTime())
            || Duration.between(r.startTime(),r.endTime()).compareTo(Duration.ofDays(7))>0 || !r.endTime().isAfter(clock.now()) || r.items()==null)
            throw new IllegalArgumentException("Provide an interruption of up to seven days and explicit remaining-work inputs.");
        config.lockOwner(owner);
        String fingerprint="interrupt:"+mapper.writeValueAsString(r);
        String saved=replay.replay(owner,key,fingerprint); if(saved!=null) return saved;
        var affected=db.query("SELECT id FROM scheduled_blocks WHERE user_id=? AND state IN ('scheduled','active') AND start_time<? AND end_time>? ORDER BY id",
            (row,n)->row.getLong(1),owner,utc(r.endTime()),utc(r.startTime()));
        var ready=db.query("SELECT b.id FROM scheduled_blocks b JOIN commitments c ON c.id=b.commitment_id WHERE b.user_id=? AND b.state='scheduled' AND c.work_state='ready' AND b.start_time<? AND b.end_time>? ORDER BY b.id",
            (row,n)->row.getLong(1),owner,utc(r.endTime()),utc(r.startTime()));
        if(r.items().stream().anyMatch(Objects::isNull) || !new HashSet<>(ready).equals(r.items().stream().map(RecoveryService.Item::blockId).collect(java.util.stream.Collectors.toSet())))
            throw new ExecutionException(400,"Provide estimates for exactly the affected unstarted task windows; refresh if the schedule changed.");
        var request=mapper.readValue(mapper.writeValueAsString(Map.of("title","Unavailable time","startTime",r.startTime(),"endTime",r.endTime())),CreateFixedCommitmentRequest.class);
        var reservation=fixed.create(owner,request);
        RecoveryService.Decision decision=null;
        if(!r.items().isEmpty()) decision=mapper.readValue(recovery.recover(owner,"interruption-"+reservation.id(),new RecoveryService.Request(r.items(),r.startTime(),r.endTime())),RecoveryService.Decision.class);
        // Active sessions, recurring instances and existing fixed commitments remain explicit user decisions.
        var attention=new ArrayList<>(affected); attention.removeAll(ready);
        var result=new Result(reservation.id(),affected,List.copyOf(attention),decision);
        return replay.save(owner,key,fingerprint,mapper.writeValueAsString(result));
    }
    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
}
