package com.atlas.backend.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded, deterministic downstream traversal for Stage 5 (04 §2.5).
 * Write-side cycle detection is unbounded and lives in {@link DependencyService}.
 */
public final class DependencyLookahead {
    public static final int DEFAULT_MAX_DEPTH = 8;
    public static final int DEFAULT_MAX_COUNT = 64;

    public record Result(List<Long> downstreamIds, boolean truncated, boolean cycleDetected) { }

    public static Result downstream(Long startId, List<CommitmentDependency> edges) {
        return downstream(startId, edges, DEFAULT_MAX_DEPTH, DEFAULT_MAX_COUNT);
    }

    public static Result downstream(Long startId, List<CommitmentDependency> edges, int maxDepth, int maxCount) {
        Objects.requireNonNull(startId, "startId must not be null");
        Objects.requireNonNull(edges, "edges must not be null");
        if (maxDepth < 1 || maxCount < 1) throw new IllegalArgumentException("bounds must be at least 1");
        Map<Long, List<Long>> adj = adjacency(edges);
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        List<Long> found = new ArrayList<>();
        boolean truncated = false;
        Map<Long, Set<Long>> explored = new HashMap<>();
        seen.add(startId);
        queue.add(new long[] {startId, 0});
        while (!queue.isEmpty()) {
            long[] node = queue.removeFirst();
            long id = node[0];
            int depth = (int) node[1];
            if (depth >= maxDepth) {
                if (adj.containsKey(id) && !adj.get(id).isEmpty()) truncated = true;
                continue;
            }
            List<Long> next = new ArrayList<>(adj.getOrDefault(id, List.of()));
            next.sort(Long::compareTo);
            for (Long child : next) {
                if (seen.contains(child)) {
                    explored.computeIfAbsent(id, key -> new HashSet<>()).add(child);
                    continue;
                }
                if (found.size() >= maxCount) {
                    truncated = true;
                    return new Result(List.copyOf(found), true, containsCycle(explored));
                }
                seen.add(child);
                explored.computeIfAbsent(id, key -> new HashSet<>()).add(child);
                found.add(child);
                queue.add(new long[] {child, depth + 1});
            }
        }
        return new Result(List.copyOf(found), truncated, containsCycle(explored));
    }

    /** Kahn's algorithm distinguishes converging/shared edges from actual cycles.
     * Only edges admitted by bounded traversal participate; no unbounded second walk.
     */
    private static boolean containsCycle(Map<Long, Set<Long>> explored) {
        Map<Long, Integer> incoming = new HashMap<>();
        explored.forEach((parent, children) -> {
            incoming.putIfAbsent(parent, 0);
            children.forEach(child -> incoming.merge(child, 1, Integer::sum));
        });
        ArrayDeque<Long> ready = new ArrayDeque<>();
        incoming.forEach((id, count) -> { if (count == 0) ready.add(id); });
        int removed = 0;
        while (!ready.isEmpty()) {
            Long parent = ready.removeFirst();
            removed++;
            for (Long child : explored.getOrDefault(parent, Set.of())) {
                if (incoming.merge(child, -1, Integer::sum) == 0) ready.add(child);
            }
        }
        return removed != incoming.size();
    }

    static Map<Long, List<Long>> adjacency(List<CommitmentDependency> edges) {
        Map<Long, List<Long>> adj = new HashMap<>();
        for (CommitmentDependency edge : edges) {
            adj.computeIfAbsent(edge.getBlockingCommitmentId(), key -> new ArrayList<>())
                .add(edge.getBlockedCommitmentId());
        }
        return adj;
    }
}
