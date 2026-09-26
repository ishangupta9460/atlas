package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;

/** Weekly local-clock configuration; only explicit user input establishes availability. */
public record WorkingHours(String timezone, List<Window> windows) {
    public record Window(int dayOfWeek, LocalTime startTime, LocalTime endTime, String kind) {
        public Window {
            if (dayOfWeek < 1 || dayOfWeek > 7 || startTime == null || endTime == null
                    || startTime.equals(endTime) || startTime.getSecond() != 0 || startTime.getNano() != 0
                    || endTime.getSecond() != 0 || endTime.getNano() != 0
                    || !Set.of("working", "sleep", "protected").contains(kind == null ? "" : kind))
                throw new IllegalArgumentException("Provide weekday 1–7, distinct minute-precision times and working/sleep/protected kind");
        }
    }

    public WorkingHours {
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone))
            throw new IllegalArgumentException("Provide an IANA timezone");
        if (windows == null || windows.size() > 224 || windows.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Provide at most 224 weekly windows");
        windows = windows.stream().sorted(Comparator.comparingInt(Window::dayOfWeek)
                .thenComparing(Window::startTime).thenComparing(Window::endTime).thenComparing(Window::kind)).toList();
        // Validate on a circular local week, including Sunday overnight versus Monday.
        for (String kind : List.of("working", "sleep", "protected")) {
            var spans = new ArrayList<int[]>();
            for (var w : windows) {
                if (!kind.equals(w.kind)) continue;
                int start = (w.dayOfWeek - 1) * 1440 + w.startTime.toSecondOfDay() / 60;
                int end = (w.dayOfWeek - 1) * 1440 + w.endTime.toSecondOfDay() / 60;
                if (end <= start) end += 1440;
                spans.add(new int[]{start, Math.min(end, 10080)});
                if (end > 10080) spans.add(new int[]{0, end - 10080});
            }
            spans.sort(Comparator.comparingInt(s -> s[0]));
            int end = -1;
            for (var span : spans) {
                if (span[0] < end) throw new IllegalArgumentException("Overlapping " + kind + " windows");
                end = span[1];
            }
        }
        // Working/protected intersections are intentional: protection always wins.
    }
}
