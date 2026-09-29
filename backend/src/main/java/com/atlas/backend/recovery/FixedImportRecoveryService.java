package com.atlas.backend.recovery;

import com.atlas.backend.ingestion.ImportService;
import com.atlas.backend.fixedcommitment.FixedCommitmentService;
import com.atlas.backend.execution.ExecutionException;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Import overlap integration belongs to Recovery; ingestion never calls upward into scheduling. */
@Service
public class FixedImportRecoveryService {
    public record Request(Long fixedCommitmentId,List<RecoveryService.Item> items) {}
    private final ImportService imports;private final FixedCommitmentService fixed;private final RecoveryService recovery;private final SchedulingConfigRepository config;
    public FixedImportRecoveryService(ImportService imports,FixedCommitmentService fixed,RecoveryService recovery,SchedulingConfigRepository config){this.imports=imports;this.fixed=fixed;this.recovery=recovery;this.config=config;}
    @Transactional public String recover(long owner,long importId,String key,Request request){
        config.lockOwner(owner);var proposal=imports.get(owner,importId,"fixed");
        if(!proposal.state().equals("approved"))throw new ExecutionException(409,"Approve the screenshot import before recovering conflicts.");
        if(request.fixedCommitmentId()==null || !proposal.result().fixedCommitmentIds().contains(request.fixedCommitmentId()))throw new ExecutionException(404,"Imported reservation not found");
        var reservation=fixed.get(owner,request.fixedCommitmentId());
        return recovery.recover(owner,key,new RecoveryService.Request(request.items(),reservation.startTime(),reservation.endTime()));
    }
}
