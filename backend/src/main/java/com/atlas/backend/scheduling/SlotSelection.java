package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;

/** Physical/capacity feasibility and Problem B ranking, separate from item priority. */
public final class SlotSelection {
    public record Context(Instant start, Instant end, Long categoryId, long id) {}
    public record Choice(Instant start, Instant end, CandidateSlotScorer.Score score) {}
    private SlotSelection() {}

    public static Long preceding(List<Context> contexts, Instant start) {
        return contexts.stream().filter(c -> !c.end().isAfter(start))
                .max(Comparator.comparing(Context::end).thenComparing(Context::start).thenComparingLong(Context::id))
                .map(Context::categoryId).orElse(null);
    }

    public static List<Choice> feasible(SchedulingFoundationService.Foundation foundation, Long category,
                                         List<Context> context) {
        if (!foundation.configured()) return List.of();
        var zone = ZoneId.of(foundation.timezone());
        var choices = new ArrayList<Choice>();
        for (var range : foundation.candidates()) {
            var starts = new TreeSet<Instant>();
            starts.add(range.earliestStart()); starts.add(range.latestStart());
            // Daily work is piecewise linear in start time. Include every work/break
            // phase boundary at midnight, then solve budget crossings within each piece.
            long cycleWork = foundation.policy().continuousWorkMinutes() * 60L;
            long cycle = cycleWork + foundation.policy().breakMinutes() * 60L;
            for (var day : foundation.days()) {
                for (var boundary : List.of(day.date().atStartOfDay(zone).toInstant(),
                        day.date().plusDays(1).atStartOfDay(zone).toInstant())) {
                    starts.add(boundary);
                    starts.add(boundary.minusSeconds(range.elapsedSeconds()));
                    for (long phase = 0; phase <= range.elapsedSeconds(); phase += cycle) {
                        starts.add(boundary.minusSeconds(phase));
                        starts.add(boundary.minusSeconds(Math.min(range.elapsedSeconds(), phase + cycleWork)));
                    }
                }
            }
            starts.removeIf(t -> t.isBefore(range.earliestStart()) || t.isAfter(range.latestStart()));
            var phases = new ArrayList<>(starts);
            for (int i = 0; i + 1 < phases.size(); i++) {
                var left = phases.get(i); var right = phases.get(i + 1);
                for (var day : foundation.days()) {
                    long atLeft = dailyWork(left, range.elapsedSeconds(), day.date(), zone, foundation.policy());
                    long atRight = dailyWork(right, range.elapsedSeconds(), day.date(), zone, foundation.policy());
                    long budget = day.capacity().remainingWorkSeconds();
                    // The foundation counts whole deliverable seconds. Locate the exact
                    // microsecond crossing, including its floor-rounding discontinuity.
                    if ((atLeft > budget && atRight <= budget) || (atLeft <= budget && atRight > budget))
                        starts.add(budgetBoundary(left, right, range.elapsedSeconds(), day.date(), zone,
                                foundation.policy(), budget, atLeft > budget));
                }
            }
            for (var start : starts) {
                if (start.isBefore(range.earliestStart()) || start.isAfter(range.latestStart())) continue;
                var end = start.plusSeconds(range.elapsedSeconds());
                boolean fits = true;
                for (var day : foundation.days()) {
                    long work = dailyWork(start, range.elapsedSeconds(), day.date(), zone, foundation.policy());
                    if (work > day.capacity().remainingWorkSeconds()) { fits = false; break; }
                }
                if (!fits) continue;
                long leftover = Duration.between(range.earliestStart(), range.availableEnd()).getSeconds() - range.elapsedSeconds();
                boolean same = category != null && category.equals(preceding(context, start));
                choices.add(new Choice(start, end, CandidateSlotScorer.score(null, null, same, leftover, range.workSeconds())));
            }
        }
        return choices.stream().distinct().sorted(Comparator.comparing(Choice::start).thenComparing(Choice::end)).toList();
    }

    private static long dailyWork(Instant start, long elapsed, LocalDate day, ZoneId zone,
                                  CapacityCalculator.Policy policy) {
        var calculator = new CapacityCalculator();
        long a = Math.max(0, Math.min(elapsed, Duration.between(start, day.atStartOfDay(zone).toInstant()).getSeconds()));
        long b = Math.max(0, Math.min(elapsed, Duration.between(start, day.plusDays(1).atStartOfDay(zone).toInstant()).getSeconds()));
        return calculator.deliverableSeconds(b, policy) - calculator.deliverableSeconds(a, policy);
    }

    private static Instant budgetBoundary(Instant left, Instant right, long elapsed, LocalDate day,
                                          ZoneId zone, CapacityCalculator.Policy policy, long budget,
                                          boolean decreasing) {
        long low = 0, high = java.time.temporal.ChronoUnit.MICROS.between(left, right);
        while (high - low > 1) {
            long middle = low + (high - low) / 2;
            boolean fits = dailyWork(left.plusNanos(middle * 1000), elapsed, day, zone, policy) <= budget;
            if (fits == decreasing) high = middle; else low = middle;
        }
        return left.plusNanos((decreasing ? high : low) * 1000);
    }

    public static Choice best(List<Choice> choices) {
        return choices.stream().min(Comparator.<Choice>comparingDouble(c -> c.score().total()).reversed()
                .thenComparing(Choice::start).thenComparing(Choice::end)).orElseThrow();
    }
}
