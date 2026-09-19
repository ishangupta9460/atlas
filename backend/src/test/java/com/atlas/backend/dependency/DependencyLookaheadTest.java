package com.atlas.backend.dependency;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyLookaheadTest {
    private static CommitmentDependency edge(long blocking, long blocked) {
        return CommitmentDependency.of(blocking, blocked);
    }

    @Test void chainIsFullyWalkedWithinBounds() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(1, 2), edge(2, 3), edge(3, 4)), 8, 64);
        assertEquals(List.of(2L, 3L, 4L), result.downstreamIds());
        assertFalse(result.truncated());
        assertFalse(result.cycleDetected());
    }

    @Test void diamondIsDeterministicAndDeduped() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(1, 3), edge(1, 2), edge(2, 4), edge(3, 4)));
        assertEquals(List.of(2L, 3L, 4L), result.downstreamIds());
        assertFalse(result.truncated());
    }

    @Test void depthBoundReportsTruncationWithoutTreatingAsEmpty() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(1, 2), edge(2, 3), edge(3, 4)), 1, 64);
        assertEquals(List.of(2L), result.downstreamIds());
        assertTrue(result.truncated());
    }

    @Test void countBoundReportsTruncation() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(1, 2), edge(1, 3), edge(1, 4)), 8, 2);
        assertEquals(List.of(2L, 3L), result.downstreamIds());
        assertTrue(result.truncated());
    }

    @Test void existingCycleTerminatesAndIsReported() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(1, 2), edge(2, 3), edge(3, 2)));
        assertEquals(List.of(2L, 3L), result.downstreamIds());
        assertTrue(result.cycleDetected());
        assertFalse(result.truncated());
    }

    @Test void disconnectedEdgesAreIgnored() {
        var result = DependencyLookahead.downstream(1L, List.of(edge(9, 10), edge(1, 2)));
        assertEquals(List.of(2L), result.downstreamIds());
    }
}
