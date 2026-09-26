package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;

/** DEC-0014: no time-of-day or learned-preference signal belongs in this stage. */
public final class Stage8CategoryContinuity {
    private Stage8CategoryContinuity() {}

    public static boolean matches(Commitment candidate, Long precedingCategoryId) {
        return precedingCategoryId != null && precedingCategoryId.equals(candidate.getCategoryId());
    }

    public static int compare(Commitment first, Commitment second, Long precedingCategoryId) {
        return Boolean.compare(matches(second, precedingCategoryId), matches(first, precedingCategoryId));
    }
}
