package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyAvailabilityTest {
    final WeeklyAvailability availability = new WeeklyAvailability();
    WorkingHours.Window window(int day, String start, String end, String kind) {
        return new WorkingHours.Window(day, LocalTime.parse(start), LocalTime.parse(end), kind);
    }
    List<TimeInterval> working(String zone, LocalDate date, String start, String end) {
        return availability.expand(new WorkingHours(zone, List.of(window(date.getDayOfWeek().getValue(), start, end, "working"))), date, date).working();
    }
    TimeInterval interval(String start, String end) { return new TimeInterval(Instant.parse(start), Instant.parse(end)); }

    @Test void normalNonWholeHourZoneUsesUserZoneAndIgnoresJvmZone() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var result = working("Asia/Kolkata", LocalDate.of(2026, 9, 25), "09:00", "17:00");
            assertEquals(List.of(interval("2026-09-25T03:30:00Z", "2026-09-25T11:30:00Z")), result);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(result, working("Asia/Kolkata", LocalDate.of(2026, 9, 25), "09:00", "17:00"));
        } finally { TimeZone.setDefault(original); }
    }

    @Test void springGapClipsInsteadOfShiftingTheWindow() {
        var day = LocalDate.of(2026, 3, 8);
        assertEquals(List.of(interval("2026-03-08T07:00:00Z", "2026-03-08T07:30:00Z")), working("America/New_York", day, "02:30", "03:30"));
        assertEquals(List.of(interval("2026-03-08T06:30:00Z", "2026-03-08T07:00:00Z")), working("America/New_York", day, "01:30", "02:30"));
        assertTrue(working("America/New_York", day, "02:10", "02:40").isEmpty());
        assertEquals(2 * 3600, TimeInterval.seconds(working("America/New_York", day, "01:00", "04:00")));
    }

    @Test void fallOverlapIncludesBothOccurrencesButNoUnconfiguredMinutes() {
        var day = LocalDate.of(2026, 11, 1);
        assertEquals(List.of(interval("2026-11-01T05:15:00Z", "2026-11-01T05:30:00Z"),
                interval("2026-11-01T06:15:00Z", "2026-11-01T06:30:00Z")),
                working("America/New_York", day, "01:15", "01:30"));
        assertEquals(4 * 3600, TimeInterval.seconds(working("America/New_York", day, "00:00", "03:00")));
    }

    @Test void supportsHalfHourTransitionsAndSkippedCivilDay() {
        assertEquals(90 * 60, TimeInterval.seconds(working("Australia/Lord_Howe", LocalDate.of(2026, 10, 4), "01:30", "03:30")));
        assertTrue(working("Pacific/Apia", LocalDate.of(2011, 12, 30), "09:00", "17:00").isEmpty());
    }

    @Test void overnightBelongsToStartingWeekdayAndProtectedWindowsOverrideWork() {
        var hours = new WorkingHours("UTC", List.of(window(7, "22:00", "06:00", "working"),
                window(7, "23:00", "05:00", "sleep"), window(1, "05:00", "05:30", "protected")));
        var expanded = availability.expand(hours, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 28));
        var monday = interval("2026-09-28T00:00:00Z", "2026-09-29T00:00:00Z");
        assertEquals(List.of(interval("2026-09-28T05:30:00Z", "2026-09-28T06:00:00Z")),
                TimeInterval.clip(TimeInterval.subtract(expanded.working(), expanded.protectedTime()), monday));
    }

    @Test void validatesCircularWeekOverlapsAndAllowsAdjacentWindows() {
        assertThrows(IllegalArgumentException.class, () -> new WorkingHours("UTC", List.of(
                window(7, "22:00", "06:00", "working"), window(1, "05:00", "07:00", "working"))));
        assertDoesNotThrow(() -> new WorkingHours("UTC", List.of(
                window(7, "22:00", "06:00", "working"), window(1, "06:00", "07:00", "working"))));
        assertThrows(IllegalArgumentException.class, () -> new WorkingHours("invalid/zone", List.of()));
        assertThrows(IllegalArgumentException.class, () -> window(1, "09:00", "09:00", "working"));
        assertThrows(IllegalArgumentException.class, () -> window(1, "09:00:01", "10:00", "working"));
        assertThrows(IllegalArgumentException.class, () -> window(0, "09:00", "10:00", "working"));
    }

    @Test void protectedRepeatedHourExcludesBothOccurrences() {
        var hours = new WorkingHours("America/New_York", List.of(window(7, "00:00", "03:00", "working"),
                window(7, "01:15", "01:30", "sleep")));
        var expanded = availability.expand(hours, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        assertEquals(30 * 60, TimeInterval.seconds(expanded.protectedTime()));
        assertEquals(210 * 60, TimeInterval.seconds(TimeInterval.subtract(expanded.working(), expanded.protectedTime())));
    }
}
