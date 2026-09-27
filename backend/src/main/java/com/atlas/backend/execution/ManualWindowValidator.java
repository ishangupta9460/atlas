package com.atlas.backend.execution;

import com.atlas.backend.scheduling.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Validates explicit user placement with the existing calendar/capacity arithmetic. */
@Component
public class ManualWindowValidator {
    private final SchedulingFoundationService foundation;
    public ManualWindowValidator(SchedulingFoundationService foundation) { this.foundation = foundation; }

    public void validate(Long owner, Instant start, Instant end) {
        var snapshot = foundation.snapshot(owner, start, end);
        if (snapshot.hours() == null)
            throw ExecutionException.conflict("Set your working hours before choosing a work window.");
        var proposed = new TimeInterval(start, end);
        var unavailable = new ArrayList<>(snapshot.expanded().protectedTime());
        unavailable.addAll(snapshot.fixed());
        var usable = TimeInterval.subtract(snapshot.expanded().working(), unavailable);
        var busy = new ArrayList<TimeInterval>();
        long buffer = snapshot.policy().bufferMinutes() * 60L;
        for (var interval : snapshot.fixed()) busy.add(new TimeInterval(interval.start().minusSeconds(buffer), interval.end().plusSeconds(buffer)));
        for (var interval : snapshot.initialBlocks()) busy.add(new TimeInterval(interval.start().minusSeconds(buffer), interval.end().plusSeconds(buffer)));
        boolean fits = TimeInterval.subtract(usable, busy).stream()
                .anyMatch(i -> !start.isBefore(i.start()) && !end.isAfter(i.end()));
        if (!fits) throw ExecutionException.conflict("Choose working time clear of protected time, fixed commitments, other blocks and buffers.");
        var occupied = new ArrayList<>(snapshot.initialBlocks());
        occupied.add(proposed);
        var zone = ZoneId.of(snapshot.hours().timezone());
        var calculator = new CapacityCalculator();
        for (var date = start.atZone(zone).toLocalDate(); !date.isAfter(end.minusNanos(1).atZone(zone).toLocalDate()); date = date.plusDays(1)) {
            var day = new TimeInterval(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
            var capacity = calculator.calculate(TimeInterval.clip(usable, day), occupied, List.of(), List.of(), snapshot.policy());
            if (capacity.occupiedWorkSeconds() > capacity.workableSeconds())
                throw ExecutionException.conflict("That window exceeds your day's workable capacity.");
        }
    }
}
