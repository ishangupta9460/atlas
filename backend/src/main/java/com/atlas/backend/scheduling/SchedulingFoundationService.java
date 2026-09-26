package com.atlas.backend.scheduling;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only snapshot of calendar reality. No placement, ranking, recovery or schedule writes. */
@Service
public class SchedulingFoundationService {
    private final SchedulingConfigRepository repository;
    private final WeeklyAvailability availability;

    public SchedulingFoundationService(SchedulingConfigRepository repository, WeeklyAvailability availability) {
        this.repository = repository; this.availability = availability;
    }

    public record DayCapacity(LocalDate date, CapacityCalculator.Capacity capacity) {}
    public record Foundation(boolean configured, String timezone, Instant startTime, Instant endTime,
                             CapacityCalculator.Policy policy, List<CandidateSlotGenerator.CandidateSlot> candidates,
                             List<DayCapacity> days) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Foundation query(Long owner, Instant start, Instant end, int workMinutes) {
        if (start == null || end == null || !end.isAfter(start)
                || start.isBefore(Instant.parse("1001-01-01T00:00:00Z"))
                || end.isAfter(Instant.parse("9998-12-31T00:00:00Z"))
                || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0
                || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0
                || workMinutes < 1 || workMinutes > 1440)
            throw new IllegalArgumentException("Choose a planning window of at most 31 days with microsecond precision and 1–1440 work minutes");
        return snapshot(owner, start, end).calculate(workMinutes, List.of());
    }

    public Snapshot snapshot(Long owner, Instant start, Instant end) {
        var policy = repository.capacity(owner).orElseGet(CapacityCalculator.Policy::defaults);
        var hours = repository.workingHours(owner).orElse(null);
        if (hours == null) return new Snapshot(null, start, end, policy, List.of(), List.of(),
                new WeeklyAvailability.Expanded(List.of(), List.of()));
        var zone = ZoneId.of(hours.timezone());
        var first = start.atZone(zone).toLocalDate();
        var last = end.minusNanos(1).atZone(zone).toLocalDate();
        long buffer = policy.bufferMinutes() * 60L;
        var loadBounds = new TimeInterval(first.atStartOfDay(zone).toInstant().minusSeconds(buffer),
                last.plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(buffer));
        return new Snapshot(hours, start, end, policy, repository.fixed(owner, loadBounds),
                repository.blocks(owner, loadBounds), availability.expand(hours, first, last));
    }

    public record Snapshot(WorkingHours hours, Instant start, Instant end, CapacityCalculator.Policy policy,
                           List<TimeInterval> fixed, List<TimeInterval> initialBlocks, WeeklyAvailability.Expanded expanded) {
        public Foundation calculate(int workMinutes, List<TimeInterval> placed) {
            if (hours == null) return new Foundation(false, null, start, end, policy, List.of(), List.of());
            var calculator = new CapacityCalculator();
            var generator = new CandidateSlotGenerator();
            var blocks = new ArrayList<>(initialBlocks);
            blocks.addAll(placed);
            var bounds = new TimeInterval(start, end);
            var zone = ZoneId.of(hours.timezone());
            var first = start.atZone(zone).toLocalDate();
            var last = end.minusNanos(1).atZone(zone).toLocalDate();
            long buffer = policy.bufferMinutes() * 60L;
            var unavailable = new ArrayList<>(expanded.protectedTime());
            unavailable.addAll(fixed);
            var usable = TimeInterval.subtract(expanded.working(), unavailable);
            var free = TimeInterval.subtract(usable, blocks);
            var bufferedBusy = new ArrayList<TimeInterval>();
            for (var interval : fixed) bufferedBusy.add(pad(interval, buffer));
            for (var interval : blocks) bufferedBusy.add(pad(interval, buffer));
            var bufferedFree = TimeInterval.subtract(usable, bufferedBusy);
            var candidates = generator.generate(bufferedFree, List.of(), bounds, workMinutes * 60L, policy);
            var days = new ArrayList<DayCapacity>();
            for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                Instant a = date.atStartOfDay(zone).toInstant(), b = date.plusDays(1).atStartOfDay(zone).toInstant();
                if (!b.isAfter(a)) continue; // A timezone may skip an entire civil day.
                var day = new TimeInterval(a, b);
                var dailyUsable = TimeInterval.clip(usable, day);
                var dailyFree = TimeInterval.clip(free, day);
                var dailyBuffered = TimeInterval.clip(bufferedFree, day);
                var capacity = calculator.calculate(dailyUsable, blocks, dailyFree, dailyBuffered, policy);
                // The daily budget accounts for the whole day; a narrow query cannot restore occupied capacity.
                long queryPhysical = TimeInterval.clip(dailyBuffered, bounds).stream()
                        .mapToLong(i -> calculator.deliverableSeconds(i.seconds(), policy)).sum();
                capacity = new CapacityCalculator.Capacity(capacity.rawFreeSeconds(), capacity.workableSeconds(),
                        capacity.reservedSeconds(), capacity.occupiedSeconds(), capacity.occupiedWorkSeconds(),
                        capacity.bufferSeconds(), Math.min(capacity.remainingWorkSeconds(), queryPhysical));
                days.add(new DayCapacity(date, capacity));
            }
            return new Foundation(true, hours.timezone(), start, end, policy, candidates, List.copyOf(days));
        }
    }

    private static TimeInterval pad(TimeInterval interval, long seconds) {
        return new TimeInterval(interval.start().minusSeconds(seconds), interval.end().plusSeconds(seconds));
    }
}
