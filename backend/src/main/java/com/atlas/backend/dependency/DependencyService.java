package com.atlas.backend.dependency;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.commitment.CommitmentRepository;
import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DependencyService {
    private final CommitmentDependencyRepository dependencies;
    private final CommitmentRepository commitments;
    private final EventRepository events;
    private final ObjectMapper mapper = new ObjectMapper();

    public DependencyService(CommitmentDependencyRepository dependencies, CommitmentRepository commitments, EventRepository events) {
        this.dependencies = dependencies;
        this.commitments = commitments;
        this.events = events;
    }

    @Transactional
    public AddResult add(Long owner, Long blockedId, Long blockingId) {
        if (blockedId == null || blockingId == null) throw DependencyException.invalid("Relationship IDs must be positive integers");
        if (blockedId.equals(blockingId)) throw DependencyException.invalid("A Commitment cannot block itself");
        lockGraph(owner);
        Commitment blocked = owned(owner, blockedId);
        Commitment blocking = owned(owner, blockingId);
        var existing = dependencies.findByBlockingCommitmentIdAndBlockedCommitmentId(blocking.getId(), blocked.getId());
        if (existing.isPresent()) return new AddResult(DependencyResponse.from(existing.get()), false);
        if (reaches(owner, blocked.getId(), blocking.getId())) throw DependencyException.cycle();
        CommitmentDependency edge = CommitmentDependency.of(blocking.getId(), blocked.getId());
        dependencies.saveAndFlush(edge);
        event(blocked, "task.dependency_added", Map.of(
            "blockingCommitmentId", blocking.getId(),
            "blockedCommitmentId", blocked.getId()));
        return new AddResult(DependencyResponse.from(edge), true);
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> listInbound(Long owner, Long blockedId) {
        owned(owner, blockedId);
        return dependencies.findByBlockedCommitmentIdOrderByBlockingCommitmentIdAsc(blockedId)
            .stream().map(DependencyResponse::from).toList();
    }

    @Transactional
    public void remove(Long owner, Long blockedId, Long blockingId) {
        if (blockedId == null || blockingId == null) throw DependencyException.invalid("Relationship IDs must be positive integers");
        lockGraph(owner);
        Commitment blocked = owned(owner, blockedId);
        owned(owner, blockingId);
        CommitmentDependency edge = dependencies.findByBlockingCommitmentIdAndBlockedCommitmentId(blockingId, blockedId)
            .orElseThrow(DependencyException::missing);
        dependencies.delete(edge);
        dependencies.flush();
        event(blocked, "task.dependency_removed", Map.of(
            "blockingCommitmentId", blockingId,
            "blockedCommitmentId", blockedId));
    }

    @Transactional(readOnly = true)
    public DependencyLookahead.Result lookahead(Long owner, Long startId) {
        owned(owner, startId);
        return DependencyLookahead.downstream(startId, dependencies.findAllOwned(owner));
    }

    private void lockGraph(Long owner) {
        if (dependencies.lockOwner(owner) == null) throw DependencyException.missing();
    }

    private Commitment owned(Long owner, Long id) {
        return commitments.findByIdAndUserId(id, owner).orElseThrow(DependencyException::missing);
    }

    /** Unbounded write-side check: adding blocking→blocked cycles if blocked already reaches blocking. */
    private boolean reaches(Long owner, Long from, Long to) {
        Map<Long, List<Long>> adj = DependencyLookahead.adjacency(dependencies.findAllOwned(owner));
        ArrayDeque<Long> stack = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        stack.push(from);
        while (!stack.isEmpty()) {
            Long node = stack.pop();
            if (!seen.add(node)) continue;
            if (node.equals(to)) return true;
            for (Long next : adj.getOrDefault(node, List.of())) stack.push(next);
        }
        return false;
    }

    private void event(Commitment blocked, String type, Map<String, Object> payload) {
        events.appendAndFlush(Event.forEntity("commitment", blocked.getId(), type, "user", null, mapper.writeValueAsString(payload)));
    }

    record AddResult(DependencyResponse body, boolean created) { }
}
