package com.atlas.backend.scheduling;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingFoundationTest {
    final CandidateSlotGenerator generator = new CandidateSlotGenerator();
    final CapacityCalculator capacity = new CapacityCalculator();
    final CapacityCalculator.Policy policy = CapacityCalculator.Policy.defaults();
    TimeInterval interval(String start, String end) {
        return new TimeInterval(Instant.parse("2026-09-25T" + start + ":00Z"),
                Instant.parse("2026-09-25T" + end + ":00Z"));
    }

    @Test void subtractsOverlappingAdjacentAndBoundaryReservationsAndFiltersShortGaps() {
        var day = interval("09:00", "17:00");
        var busy = List.of(interval("08:00", "09:30"), interval("10:00", "11:00"),
                interval("10:30", "12:00"), interval("12:00", "13:00"), interval("16:00", "18:00"));
        var result = generator.generate(List.of(day), busy, day, 31 * 60, policy);
        assertEquals(1, result.size());
        assertEquals(interval("13:00", "16:00").start(), result.get(0).earliestStart());
        assertEquals(Instant.parse("2026-09-25T15:29:00Z"), result.get(0).latestStart());
        assertEquals(2, generator.generate(List.of(day), busy, day, 30 * 60, policy).size());
    }

    @Test void handlesEmptyFullAndExactDurationWindows() {
        var day = interval("09:00", "17:00");
        assertEquals(1, generator.generate(List.of(day), List.of(), day, 50 * 60, policy).size());
        assertTrue(generator.generate(List.of(), List.of(), day, 60, policy).isEmpty());
        assertTrue(generator.generate(List.of(day), List.of(day), day, 60, policy).isEmpty());
        var shortWindow = interval("09:00", "09:50");
        var slot = generator.generate(List.of(shortWindow), List.of(), day, 50 * 60, policy).get(0);
        assertEquals(slot.earliestStart(), slot.latestStart());
        assertTrue(generator.generate(List.of(shortWindow), List.of(), day, 50 * 60 + 1, policy).isEmpty());
    }

    @Test void deterministicAcrossInputPermutationsAndRepeatedRuns() {
        var day = interval("09:00", "17:00");
        var busy = new ArrayList<>(List.of(interval("10:00", "11:00"), interval("10:30", "12:00"),
                interval("15:00", "16:00"), interval("12:00", "13:00")));
        var expected = generator.generate(List.of(day), busy, day, 20 * 60, policy);
        for (int seed = 0; seed < 100; seed++) {
            Collections.shuffle(busy, new Random(seed));
            assertEquals(expected, generator.generate(List.of(day), busy, day, 20 * 60, policy));
        }
    }

    @Test void countsSeventyPercentWithoutChargingBuffersTwice() {
        var day = interval("09:00", "17:00");
        var occupied = interval("10:00", "11:00");
        var free = TimeInterval.subtract(List.of(day), List.of(occupied));
        var buffered = TimeInterval.subtract(List.of(day), List.of(interval("09:50", "11:10")));
        var result = capacity.calculate(List.of(day), List.of(occupied), free, buffered, policy);
        assertEquals(480 * 60, result.rawFreeSeconds());
        assertEquals(336 * 60, result.workableSeconds());
        assertEquals(144 * 60, result.reservedSeconds());
        assertEquals(50 * 60, result.occupiedWorkSeconds());
        assertEquals(20 * 60, result.bufferSeconds());
        assertEquals(286 * 60, result.remainingWorkSeconds());
    }

    @Test void longBlocksAccountForBreaksAndPhysicalFragmentationCapsCapacity() {
        assertEquals(3000, capacity.elapsedSeconds(3000, policy));
        assertEquals(3601, capacity.elapsedSeconds(3001, policy));
        assertEquals(6600, capacity.elapsedSeconds(6000, policy));
        assertEquals(3000, capacity.deliverableSeconds(3600, policy));
        assertEquals(6000, capacity.deliverableSeconds(6600, policy));
        var day = interval("09:00", "10:00");
        var occupied = interval("09:00", "09:50");
        var result = capacity.calculate(List.of(day), List.of(occupied),
                List.of(interval("09:50", "10:00")), List.of(), policy);
        assertEquals(0, result.remainingWorkSeconds());
        var zero = new CapacityCalculator.Policy(BigDecimal.ZERO, 10, 50, 10);
        assertEquals(0, capacity.calculate(List.of(day), List.of(), List.of(day), List.of(day), zero).remainingWorkSeconds());
    }

    @Test void rejectsInvalidDurationsAndPolicies() {
        assertThrows(IllegalArgumentException.class, () -> capacity.elapsedSeconds(0, policy));
        assertThrows(IllegalArgumentException.class, () -> new CapacityCalculator.Policy(new BigDecimal("1.01"), 10, 50, 10));
    }

    @Test void adjacentShortBlocksDoNotAcquireAnInventedInternalBreak() {
        var day = interval("09:00", "17:00");
        var occupied = List.of(interval("10:00", "10:30"), interval("10:30", "11:00"));
        var free = TimeInterval.subtract(List.of(day), occupied);
        assertEquals(3600, capacity.calculate(List.of(day), occupied, free, free, policy).occupiedWorkSeconds());
    }

    @Test void midnightClippingPreservesTheExistingBlocksBreakOrigin() {
        var day = new TimeInterval(Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-25T01:00:00Z"));
        var block = new TimeInterval(Instant.parse("2026-09-24T23:30:00Z"), Instant.parse("2026-09-25T00:30:00Z"));
        var free = TimeInterval.subtract(List.of(day), List.of(block));
        var result = capacity.calculate(List.of(day), List.of(block), free, free, policy);
        assertEquals(30 * 60, result.occupiedSeconds());
        assertEquals(20 * 60, result.occupiedWorkSeconds());
        assertEquals(22 * 60, result.remainingWorkSeconds());
    }

    @Test void candidateSlotsDetailedScenarios() {
        var day = interval("09:00", "17:00");

        // 1. One fixed commitment splitting day into 2 slots
        var oneFixed = List.of(interval("12:00", "13:00"));
        var slotsOne = generator.generate(List.of(day), oneFixed, day, 60 * 60, policy);
        assertEquals(2, slotsOne.size());
        assertEquals(interval("09:00", "12:00").start(), slotsOne.get(0).earliestStart());
        assertEquals(interval("13:00", "17:00").start(), slotsOne.get(1).earliestStart());

        // 2. Exact boundary fit: 50 min work fits exactly in 50 min window, but 50 min + 1 sec does not
        var fiftyMinWindow = interval("09:00", "09:50");
        var exactFit = generator.generate(List.of(fiftyMinWindow), List.of(), day, 50 * 60, policy);
        assertEquals(1, exactFit.size());
        assertEquals(exactFit.get(0).earliestStart(), exactFit.get(0).latestStart());
        assertTrue(generator.generate(List.of(fiftyMinWindow), List.of(), day, 50 * 60 + 1, policy).isEmpty());

        // 3. Completely full day -> 0 candidate slots
        var completelyFull = List.of(interval("09:00", "17:00"));
        assertTrue(generator.generate(List.of(day), completelyFull, day, 15 * 60, policy).isEmpty());

        // 4. Insufficient remaining duration (window is 29 minutes, requested work is 30 minutes)
        var busySurrounding = List.of(interval("09:00", "12:00"), interval("12:29", "17:00"));
        assertTrue(generator.generate(List.of(day), busySurrounding, day, 30 * 60, policy).isEmpty());
        assertEquals(1, generator.generate(List.of(day), busySurrounding, day, 29 * 60, policy).size());
    }

    @Test void customCapacityPolicyScenarios() {
        var day = interval("09:00", "17:00"); // 8 hours = 480 min = 28800 sec
        // 50% fraction, 15 min buffer, 25 min continuous, 5 min break
        var custom = new CapacityCalculator.Policy(new BigDecimal("0.50"), 15, 25, 5);
        var occupied = List.of(interval("10:00", "11:00")); // 1 hour = 60 min
        var free = TimeInterval.subtract(List.of(day), occupied);
        var buffered = TimeInterval.subtract(List.of(day), List.of(interval("09:45", "11:15")));
        var result = capacity.calculate(List.of(day), occupied, free, buffered, custom);

        assertEquals(28800, result.rawFreeSeconds());
        assertEquals(14400, result.workableSeconds()); // 50% of 28800
        assertEquals(14400, result.reservedSeconds());
        // For occupied block of 60 min: 25 min work + 5 min break + 25 min work + 5 min break = 50 min work
        assertEquals(50 * 60, result.occupiedWorkSeconds());
        assertEquals(30 * 60, result.bufferSeconds()); // 2 * 15 min = 30 min buffer
        assertEquals(14400 - 50 * 60, result.remainingWorkSeconds());

        // 100% fraction (workableFraction = 1.0) with 60m continuous / 10m break:
        // 480 elapsed min has 6 full 70-min cycles (360 min work) + 60 min work = 420 min = 25200 sec deliverable work
        var fullCapacity = new CapacityCalculator.Policy(BigDecimal.ONE, 0, 60, 10);
        var fullResult = capacity.calculate(List.of(day), List.of(), List.of(day), List.of(day), fullCapacity);
        assertEquals(28800, fullResult.rawFreeSeconds());
        assertEquals(28800, fullResult.workableSeconds());
        assertEquals(0, fullResult.reservedSeconds());
        assertEquals(25200, fullResult.remainingWorkSeconds());
    }
}
