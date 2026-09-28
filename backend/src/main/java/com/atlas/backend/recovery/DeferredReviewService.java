package com.atlas.backend.recovery;

import com.atlas.backend.commitment.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.scheduling.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** On-demand batched review: no optimizer, deletion, target change or per-item notification. */
@Service
public class DeferredReviewService {
    private final CommitmentRepository tasks;
    private final CommitmentService domain;
    private final SchedulingConfigRepository config;
    private final ExecutionIdempotency replay;
    private final SchedulingPipeline pipeline;
    private final ObjectMapper mapper=new ObjectMapper();
    public DeferredReviewService(CommitmentRepository tasks,CommitmentService domain,SchedulingConfigRepository config,ExecutionIdempotency replay,SchedulingPipeline pipeline) {
        this.tasks=tasks; this.domain=domain; this.config=config; this.replay=replay;this.pipeline=pipeline;
    }
    @Transactional
    public SchedulingPlanner.Plan preview(Long owner,SchedulingPipeline.Request request) {
        config.lockOwner(owner);
        if(request==null || request.work()==null) throw new IllegalArgumentException("Provide explicit estimates and a review window.");
        for(var w:request.work()) if(w==null || w.commitmentId()==null || !domain.get(owner,w.commitmentId()).workState().equals("deferred"))
            throw new ExecutionException(409,"Review deferred tasks only.");
        return pipeline.preview(owner,request,Set.of(),List.of(),true);
    }
    @Transactional
    public List<CommitmentResponse> review(Long owner) {
        config.lockOwner(owner);
        return tasks.lockAllOwned(owner).stream().filter(c->c.getWorkState().equals("deferred")).map(c->domain.get(owner,c.getId())).toList();
    }
    @Transactional
    public String reactivate(Long owner,String key,List<Long> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>1000 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())
            throw new IllegalArgumentException("Choose distinct deferred tasks to return to active planning.");
        config.lockOwner(owner);
        var sorted=ids.stream().sorted().toList(); String fingerprint="deferred-review:"+mapper.writeValueAsString(sorted);
        String saved=replay.replay(owner,key,fingerprint); if(saved!=null) return saved;
        for(long id:sorted) domain.recoveryDeferral(owner,id,false,"You returned this deferred work to active planning after reviewing the backlog.");
        return replay.save(owner,key,fingerprint,mapper.writeValueAsString(sorted));
    }
}
