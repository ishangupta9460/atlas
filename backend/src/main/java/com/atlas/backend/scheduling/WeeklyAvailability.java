package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Exact inverse of local-clock windows: missing times vanish, repeated times occur twice. */
@Component
public class WeeklyAvailability {
    public record Expanded(List<TimeInterval> working, List<TimeInterval> protectedTime) {}

    public Expanded expand(WorkingHours hours, LocalDate first, LocalDate last) {
        ZoneId zone = ZoneId.of(hours.timezone());
        var work = new ArrayList<TimeInterval>();
        var protectedTime = new ArrayList<TimeInterval>();
        // Include the preceding weekday's overnight tail.
        for (LocalDate day = first.minusDays(1); !day.isAfter(last); day = day.plusDays(1)) {
            for (var window : hours.windows()) {
                if (window.dayOfWeek() != day.getDayOfWeek().getValue()) continue;
                LocalDateTime start = day.atTime(window.startTime());
                LocalDateTime end = day.atTime(window.endTime());
                if (!end.isAfter(start)) end = end.plusDays(1);
                var target = window.kind().equals("working") ? work : protectedTime;
                target.addAll(resolve(start, end, zone));
            }
        }
        return new Expanded(TimeInterval.union(work), TimeInterval.union(protectedTime));
    }

    static List<TimeInterval> resolve(LocalDateTime start, LocalDateTime end, ZoneId zone) {
        var result = new ArrayList<TimeInterval>();
        Instant cursor = start.toInstant(ZoneOffset.MAX), limit = end.toInstant(ZoneOffset.MIN);
        var rules = zone.getRules();
        while (cursor.isBefore(limit)) {
            ZoneOffset offset = rules.getOffset(cursor);
            var transition = rules.nextTransition(cursor);
            Instant segmentEnd = transition == null ? limit : TimeInterval.min(limit, transition.getInstant());
            Instant a = TimeInterval.max(cursor, start.toInstant(offset));
            Instant b = TimeInterval.min(segmentEnd, end.toInstant(offset));
            if (b.isAfter(a)) result.add(new TimeInterval(a, b));
            cursor = segmentEnd;
        }
        return TimeInterval.union(result);
    }
}
