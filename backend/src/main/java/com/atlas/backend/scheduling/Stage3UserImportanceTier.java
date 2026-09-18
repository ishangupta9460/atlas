package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import java.util.Comparator;
import java.util.Objects;

/**
 * Implements Stage 3 (User-Defined Importance Tier) of the Atlas Scheduling Engine
 * as specified in 04_SCHEDULING_ENGINE.md §2.
 *
 * <p>Stage 3 performs pure tier-based comparison using stored user importance levels
 * (CRITICAL > HIGH > MEDIUM > LOW).
 *
 * <p>Responsibility Boundary (per SCH-005 spec & DOM-003 invariant):
 * <ul>
 *   <li>DOM-003 resolves category defaults and explicit overrides on Commitment creation/update,
 *       storing the effective value in {@link Commitment#getImportance()}.</li>
 *   <li>SCH-005 consumes the STORED effective importance from {@link Commitment#getImportance()} directly.</li>
 *   <li>Stage 3 does NOT re-query or dynamically re-evaluate {@code Category.default_importance}.</li>
 * </ul>
 */
public final class Stage3UserImportanceTier implements Comparator<Commitment> {

    private static final Stage3UserImportanceTier INSTANCE = new Stage3UserImportanceTier();

    public static Stage3UserImportanceTier instance() {
        return INSTANCE;
    }

    /**
     * Compares two ImportanceTier values.
     *
     * @param first  the first tier to compare
     * @param second the second tier to compare
     * @return a positive integer if first outranks second, negative if second outranks first, 0 if equal
     */
    public static int compareTiers(ImportanceTier first, ImportanceTier second) {
        Objects.requireNonNull(first, "first tier must not be null");
        Objects.requireNonNull(second, "second tier must not be null");
        return Integer.compare(first.getRank(), second.getRank());
    }

    /**
     * Compares two stored importance string values (e.g. from Commitment.getImportance()).
     *
     * @param firstImportance  first importance string
     * @param secondImportance second importance string
     * @return a positive integer if first outranks second, negative if second outranks first, 0 if equal
     */
    public static int compareImportanceStrings(String firstImportance, String secondImportance) {
        ImportanceTier tierA = ImportanceTier.from(firstImportance);
        ImportanceTier tierB = ImportanceTier.from(secondImportance);
        return compareTiers(tierA, tierB);
    }

    /**
     * Compares two Commitments by their stored effective importance.
     *
     * @param first  first commitment
     * @param second second commitment
     * @return positive if first outranks second, negative if second outranks first, 0 if equal
     */
    public static int compareCommitmentImportance(Commitment first, Commitment second) {
        Objects.requireNonNull(first, "first commitment must not be null");
        Objects.requireNonNull(second, "second commitment must not be null");
        return compareImportanceStrings(first.getImportance(), second.getImportance());
    }

    /**
     * Comparator implementation for sorting Commitments in descending order of Stage 3 importance
     * (higher importance tier comes first).
     */
    @Override
    public int compare(Commitment c1, Commitment c2) {
        // Reverse standard rank order so higher rank comes first in collection sorting
        return compareCommitmentImportance(c2, c1);
    }
}
