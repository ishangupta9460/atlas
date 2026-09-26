package com.atlas.backend.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Half-open UTC interval. Pure arithmetic, independent of database and machine timezone. */
public record TimeInterval(Instant start, Instant end) {
    public TimeInterval {
        if (start == null || end == null || !end.isAfter(start))
            throw new IllegalArgumentException("An interval must have start < end");
    }

    public long seconds() { return Duration.between(start, end).getSeconds(); }

    public static List<TimeInterval> union(List<TimeInterval> intervals) {
        return merge(intervals, true);
    }

    static List<TimeInterval> merge(List<TimeInterval> intervals, boolean joinAdjacent) {
        var sorted = intervals.stream().sorted(Comparator.comparing(TimeInterval::start)
                .thenComparing(TimeInterval::end)).toList();
        var result = new ArrayList<TimeInterval>();
        for (var next : sorted) {
            if (result.isEmpty() || next.start.isAfter(result.get(result.size() - 1).end)
                    || (!joinAdjacent && next.start.equals(result.get(result.size() - 1).end))) {
                result.add(next);
            } else {
                var previous = result.remove(result.size() - 1);
                result.add(new TimeInterval(previous.start, max(previous.end, next.end)));
            }
        }
        return List.copyOf(result);
    }

    /** Sort + sweep: overlapping reservations are counted once, adjacency leaves no gap. */
    public static List<TimeInterval> subtract(List<TimeInterval> available, List<TimeInterval> unavailable) {
        var open = union(available);
        var busy = union(unavailable);
        var result = new ArrayList<TimeInterval>();
        int first = 0;
        for (var window : open) {
            Instant cursor = window.start;
            while (first < busy.size() && !busy.get(first).end.isAfter(cursor)) first++;
            for (int i = first; i < busy.size() && busy.get(i).start.isBefore(window.end); i++) {
                var reservation = busy.get(i);
                if (reservation.start.isAfter(cursor))
                    result.add(new TimeInterval(cursor, min(reservation.start, window.end)));
                cursor = max(cursor, reservation.end);
                if (!cursor.isBefore(window.end)) break;
            }
            if (cursor.isBefore(window.end)) result.add(new TimeInterval(cursor, window.end));
        }
        return List.copyOf(result);
    }

    public static List<TimeInterval> clip(List<TimeInterval> intervals, TimeInterval bounds) {
        var result = new ArrayList<TimeInterval>();
        for (var interval : intervals) {
            Instant start = max(interval.start, bounds.start), end = min(interval.end, bounds.end);
            if (end.isAfter(start)) result.add(new TimeInterval(start, end));
        }
        return union(result);
    }

    public static long seconds(List<TimeInterval> intervals) {
        return union(intervals).stream().mapToLong(TimeInterval::seconds).sum();
    }

    static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
}
