package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.dependency.CommitmentDependency;
import java.time.Instant;
import java.util.*;

/** Pure snapshot-to-plan loop. Neither database access nor clock reads enter ranking. */
public final class SchedulingPlanner {
    public record Placement(Long commitmentId, Instant startTime, Instant endTime, String placementReason,
                            CandidateSlotScorer.Score slotScore, Stage7HistoricalCalibration.Result calibration) {}
    public record Tie(Long selectedCommitmentId, Long otherCommitmentId, String message) {}
    public record Plan(List<Placement> placements, List<Long> unplaced, List<Tie> importantTies) {}

    public Plan plan(List<Commitment> owned, List<CommitmentDependency> edges, Set<Long> atRiskGoals,
                     Set<Long> alreadyScheduled, Map<Long, Integer> minutes, Long instruction,
                     SchedulingFoundationService.Snapshot calendar, List<SlotSelection.Context> initialContext) {
        var snapshot = new HashMap<Long, Commitment>();
        owned.forEach(c -> snapshot.put(c.getId(), c));
        var blocked = new HashSet<Long>();
        for (var edge : edges) {
            var parent = snapshot.get(edge.getBlockingCommitmentId());
            if (parent == null || !"completed".equals(parent.getWorkState())) blocked.add(edge.getBlockedCommitmentId());
        }
        var pool = new ArrayList<WorkRanking.Candidate>();
        for (var c : Stage0HardConstraintGate.movable(owned)) {
            if (!minutes.containsKey(c.getId()) || !"ready".equals(c.getWorkState())
                    || alreadyScheduled.contains(c.getId()) || blocked.contains(c.getId())) continue;
            var signal = Stage5DependencyTier.evaluate(c, edges, snapshot);
            if (signal.cycleDetected()) continue;
            pool.add(new WorkRanking.Candidate(c, signal, c.getGoalId() != null && atRiskGoals.contains(c.getGoalId())));
        }
        var placed = new ArrayList<TimeInterval>();
        var context = new ArrayList<>(initialContext);
        var result = new ArrayList<Placement>();
        var ties = new ArrayList<Tie>();
        while (!pool.isEmpty()) {
            var byDuration = new HashMap<Integer, SchedulingFoundationService.Foundation>();
            var options = new HashMap<Long, List<SlotSelection.Choice>>();
            for (var candidate : pool) {
                int work = minutes.get(candidate.work().getId());
                var foundation = byDuration.computeIfAbsent(work, n -> calendar.calculate(n, placed));
                options.put(candidate.work().getId(), SlotSelection.feasible(foundation, candidate.work().getCategoryId(), context));
            }
            var feasible = pool.stream().filter(c -> !options.get(c.work().getId()).isEmpty()).toList();
            if (feasible.isEmpty()) break;
            var earlier = new WorkRanking(null);
            var top = feasible.stream().min((a, b) -> earlier.compareThroughStage6(a, b).order()).orElseThrow();
            var contenders = feasible.stream().filter(c -> earlier.compareThroughStage6(top, c).order() == 0).toList();
            var sharedStarts = new TreeSet<Instant>();
            options.get(top.work().getId()).forEach(s -> sharedStarts.add(s.start()));
            for (var contender : contenders) {
                var starts = options.get(contender.work().getId()).stream().map(SlotSelection.Choice::start)
                        .collect(java.util.stream.Collectors.toSet());
                sharedStarts.retainAll(starts);
            }
            var ranking = new WorkRanking(sharedStarts.isEmpty() ? null : SlotSelection.preceding(context, sharedStarts.first()));
            var ordered = new ArrayList<>(feasible);
            ordered.sort((a, b) -> ranking.compare(a, b).order());
            var override = Stage1ExplicitInstruction.select(feasible.stream().map(WorkRanking.Candidate::work).toList(), instruction);
            var winner = override.isPresent() ? feasible.stream().filter(c -> c.work().getId().equals(instruction)).findFirst().orElseThrow() : ordered.get(0);
            String reason = "This is the remaining eligible work that fits your available capacity.";
            if (override.isPresent()) reason = "You explicitly asked to schedule this work first.";
            else if (ordered.size() > 1) {
                reason = ranking.compare(winner, ordered.get(1)).reason();
                for (var other : ordered.subList(1, ordered.size())) {
                    if (ranking.compare(winner, other).importantTie())
                        ties.add(new Tie(winner.work().getId(), other.work().getId(),
                                "These important items were equally ranked; creation order selected the first."));
                }
            }
            var slot = SlotSelection.best(options.get(winner.work().getId()));
            reason += " The selected window fits working hours, breaks, buffers and remaining daily capacity."
                    + (slot.score().continuity() == 1 ? " It continues the preceding category." : "")
                    + " Slot fit accounts for the usable gap left afterward; equal fits use the earliest time.";
            result.add(new Placement(winner.work().getId(), slot.start(), slot.end(), reason, slot.score(), Stage7HistoricalCalibration.missingHistory()));
            placed.add(new TimeInterval(slot.start(), slot.end()));
            context.add(new SlotSelection.Context(slot.start(), slot.end(), winner.work().getCategoryId(), winner.work().getId()));
            pool.remove(winner);
        }
        var selected = result.stream().map(Placement::commitmentId).collect(java.util.stream.Collectors.toSet());
        return new Plan(List.copyOf(result), minutes.keySet().stream().filter(id -> !selected.contains(id)).sorted().toList(), List.copyOf(ties));
    }
}
