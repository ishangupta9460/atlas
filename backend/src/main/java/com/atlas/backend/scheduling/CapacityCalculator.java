package com.atlas.backend.scheduling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Single per-user model (04 §4). Breaks reduce deliverable work; buffers share the reserve. */
@Component
public class CapacityCalculator {
    public record Policy(BigDecimal workableFraction, int bufferMinutes, int continuousWorkMinutes, int breakMinutes) {
        public Policy {
            if (workableFraction == null || workableFraction.signum() < 0
                    || workableFraction.compareTo(BigDecimal.ONE) > 0 || workableFraction.scale() > 4
                    || bufferMinutes < 0 || bufferMinutes > 60 || continuousWorkMinutes < 1
                    || continuousWorkMinutes > 240 || breakMinutes < 1 || breakMinutes > 60)
                throw new IllegalArgumentException("Invalid capacity policy");
            workableFraction = workableFraction.stripTrailingZeros();
        }
        public static Policy defaults() { return new Policy(new BigDecimal("0.70"), 10, 50, 10); }
    }

    public record Capacity(long rawFreeSeconds, long workableSeconds, long reservedSeconds,
                           long occupiedSeconds, long occupiedWorkSeconds, long bufferSeconds,
                           long remainingWorkSeconds) {}

    /** Requested duration means deliverable work; no trailing break is needed. */
    public long elapsedSeconds(long workSeconds, Policy policy) {
        if (workSeconds <= 0 || workSeconds > 86400)
            throw new IllegalArgumentException("Work duration must be between 1 second and 24 hours");
        long breaks = (workSeconds - 1) / (policy.continuousWorkMinutes * 60L);
        return workSeconds + breaks * policy.breakMinutes * 60L;
    }

    public long deliverableSeconds(long elapsedSeconds, Policy policy) {
        if (elapsedSeconds < 0) throw new IllegalArgumentException("Elapsed time must not be negative");
        long work = policy.continuousWorkMinutes * 60L, cycle = work + policy.breakMinutes * 60L;
        return (elapsedSeconds / cycle) * work + Math.min(work, elapsedSeconds % cycle);
    }

    /** Inputs cover one local day (or its requested portion); preserve adjacent block boundaries for breaks. */
    public Capacity calculate(List<TimeInterval> usable, List<TimeInterval> occupied,
                              List<TimeInterval> free, List<TimeInterval> bufferedFree, Policy policy) {
        long raw = TimeInterval.seconds(usable);
        long workable = policy.workableFraction.multiply(BigDecimal.valueOf(raw))
                .setScale(0, RoundingMode.FLOOR).longValueExact();
        long occupiedSeconds = 0, occupiedWork = 0;
        // Preserve each block's break-cycle origin even when clipped by midnight or working hours.
        // Overlapping legacy reservations are counted once; adjacent blocks retain their boundary.
        var usableUnion = TimeInterval.union(usable);
        int first = 0;
        for (var block : TimeInterval.merge(occupied, false)) {
            while (first < usableUnion.size() && !usableUnion.get(first).end().isAfter(block.start())) first++;
            for (int i = first; i < usableUnion.size() && usableUnion.get(i).start().isBefore(block.end()); i++) {
                var window = usableUnion.get(i);
                var start = TimeInterval.max(block.start(), window.start());
                var end = TimeInterval.min(block.end(), window.end());
                if (!end.isAfter(start)) continue;
                occupiedSeconds += java.time.Duration.between(start, end).getSeconds();
                occupiedWork += deliverableSeconds(java.time.Duration.between(block.start(), end).getSeconds(), policy)
                        - deliverableSeconds(java.time.Duration.between(block.start(), start).getSeconds(), policy);
            }
        }
        long buffer = TimeInterval.seconds(free) - TimeInterval.seconds(bufferedFree);
        long physicalWork = TimeInterval.union(bufferedFree).stream()
                .mapToLong(i -> deliverableSeconds(i.seconds(), policy)).sum();
        // Do not subtract buffers from the 70% again: they consume the non-workable reserve.
        long remaining = Math.min(Math.max(0, workable - occupiedWork), physicalWork);
        return new Capacity(raw, workable, raw - workable, occupiedSeconds, occupiedWork, buffer, remaining);
    }
}
