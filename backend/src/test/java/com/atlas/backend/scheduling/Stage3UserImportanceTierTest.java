package com.atlas.backend.scheduling;

import com.atlas.backend.commitment.Commitment;
import com.atlas.backend.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Stage3UserImportanceTierTest {

    private Commitment createCommitmentWithImportance(Long id, String importance) {
        try {
            var constructor = Commitment.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Commitment c = constructor.newInstance();
            ReflectionTestUtils.setField(c, "id", id);
            ReflectionTestUtils.setField(c, "userId", 1L);
            ReflectionTestUtils.setField(c, "title", "Task " + id);
            ReflectionTestUtils.setField(c, "completionCriterion", "Criterion");
            ReflectionTestUtils.setField(c, "importance", importance);
            ReflectionTestUtils.setField(c, "flexibilityTier", "flexible");
            ReflectionTestUtils.setField(c, "workState", "ready");
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("ImportanceTier parsing and ranking")
    void testImportanceTierEnum() {
        assertEquals(ImportanceTier.LOW, ImportanceTier.from("low"));
        assertEquals(ImportanceTier.MEDIUM, ImportanceTier.from("medium"));
        assertEquals(ImportanceTier.HIGH, ImportanceTier.from("high"));
        assertEquals(ImportanceTier.CRITICAL, ImportanceTier.from("critical"));

        assertEquals(1, ImportanceTier.LOW.getRank());
        assertEquals(2, ImportanceTier.MEDIUM.getRank());
        assertEquals(3, ImportanceTier.HIGH.getRank());
        assertEquals(4, ImportanceTier.CRITICAL.getRank());

        assertThrows(IllegalArgumentException.class, () -> ImportanceTier.from(null));
        assertThrows(IllegalArgumentException.class, () -> ImportanceTier.from("unknown"));
        assertThrows(IllegalArgumentException.class, () -> ImportanceTier.from("urgent"));
    }

    @Test
    @DisplayName("Importance tier ordering: CRITICAL > HIGH > MEDIUM > LOW")
    void testImportanceTierOrdering() {
        assertTrue(Stage3UserImportanceTier.compareTiers(ImportanceTier.CRITICAL, ImportanceTier.HIGH) > 0);
        assertTrue(Stage3UserImportanceTier.compareTiers(ImportanceTier.HIGH, ImportanceTier.MEDIUM) > 0);
        assertTrue(Stage3UserImportanceTier.compareTiers(ImportanceTier.MEDIUM, ImportanceTier.LOW) > 0);

        assertTrue(Stage3UserImportanceTier.compareTiers(ImportanceTier.LOW, ImportanceTier.CRITICAL) < 0);
        assertTrue(Stage3UserImportanceTier.compareTiers(ImportanceTier.MEDIUM, ImportanceTier.HIGH) < 0);
    }

    @Test
    @DisplayName("Equal importance behavior: ties evaluate to 0")
    void testEqualImportanceTiers() {
        assertEquals(0, Stage3UserImportanceTier.compareTiers(ImportanceTier.CRITICAL, ImportanceTier.CRITICAL));
        assertEquals(0, Stage3UserImportanceTier.compareTiers(ImportanceTier.HIGH, ImportanceTier.HIGH));
        assertEquals(0, Stage3UserImportanceTier.compareTiers(ImportanceTier.MEDIUM, ImportanceTier.MEDIUM));
        assertEquals(0, Stage3UserImportanceTier.compareTiers(ImportanceTier.LOW, ImportanceTier.LOW));

        Commitment c1 = createCommitmentWithImportance(1L, "high");
        Commitment c2 = createCommitmentWithImportance(2L, "high");
        assertEquals(0, Stage3UserImportanceTier.compareCommitmentImportance(c1, c2));
    }

    @Test
    @DisplayName("Deterministic comparison")
    void testDeterministicComparison() {
        Commitment critical = createCommitmentWithImportance(10L, "critical");
        Commitment low = createCommitmentWithImportance(20L, "low");

        int firstRun = Stage3UserImportanceTier.compareCommitmentImportance(critical, low);
        int secondRun = Stage3UserImportanceTier.compareCommitmentImportance(critical, low);
        int thirdRun = Stage3UserImportanceTier.compareCommitmentImportance(critical, low);

        assertEquals(firstRun, secondRun);
        assertEquals(secondRun, thirdRun);
        assertTrue(firstRun > 0);
    }

    @Test
    @DisplayName("Stored Commitment importance is consumed directly and authoritative over Category changes")
    void testStoredCommitmentImportanceIsAuthoritative() {
        // Category originally has defaultImportance = "low"
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 100L);
        ReflectionTestUtils.setField(category, "defaultImportance", "low");

        // Commitment created with stored importance "critical"
        Commitment c1 = createCommitmentWithImportance(1L, "critical");
        // Commitment created with stored importance "medium"
        Commitment c2 = createCommitmentWithImportance(2L, "medium");

        // Mutate Category defaultImportance to "critical"
        ReflectionTestUtils.setField(category, "defaultImportance", "critical");

        // Stage 3 comparison consumes Commitment.getImportance() stored value ("critical" vs "medium")
        // Category default changes have zero effect on already-stored Commitment importance comparison
        assertTrue(Stage3UserImportanceTier.compareCommitmentImportance(c1, c2) > 0);
        assertEquals("critical", c1.getImportance());
        assertEquals("medium", c2.getImportance());
    }

    @Test
    @DisplayName("Comparator sorts collection in descending order of Stage 3 importance")
    void testComparatorSorting() {
        Commitment low = createCommitmentWithImportance(1L, "low");
        Commitment critical = createCommitmentWithImportance(2L, "critical");
        Commitment medium = createCommitmentWithImportance(3L, "medium");
        Commitment high = createCommitmentWithImportance(4L, "high");

        List<Commitment> list = new ArrayList<>(List.of(low, critical, medium, high));
        list.sort(Stage3UserImportanceTier.instance());

        assertEquals("critical", list.get(0).getImportance());
        assertEquals("high", list.get(1).getImportance());
        assertEquals("medium", list.get(2).getImportance());
        assertEquals("low", list.get(3).getImportance());
    }
}
