package com.atlas.backend.commitment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CommitmentTest {
    static Commitment.Fields fields(String title, String criterion) { return new Commitment.Fields(title,null,criterion,null,null,null,null,"high","flexible"); }
    @Test void readinessRequiresBothFieldsAndPreventsClearingAfterDraft() {
        for (String title : new String[]{null,"","  "}) {
            var value=Commitment.create(1L,fields(title,"done"));
            assertFalse(value.establishReadiness());
            value.update(fields("task","done")); assertTrue(value.establishReadiness());
            assertThrows(CommitmentException.class,()->value.update(fields(title,"done")));
        }
        var value=Commitment.create(1L,fields("task",null)); assertFalse(value.establishReadiness());
        value.update(fields("task","done")); assertTrue(value.establishReadiness());
        assertThrows(CommitmentException.class,()->value.update(fields("task",null)));
    }
    @Test void everyStatePairIsGuarded() {
        var legal=Set.of("draft:ready","ready:in_progress","in_progress:completed","in_progress:ready");
        for (String from:Commitment.STATES) for(String to:Commitment.STATES) {
            var value=Commitment.create(1L,fields("task","done"));
            ReflectionTestUtils.setField(value,"workState",from);
            if (legal.contains(from+":"+to)) { value.transition(to,true,true); assertEquals(to,value.getWorkState()); }
            else { assertThrows(CommitmentException.class,()->value.transition(to,true,true)); assertEquals(from,value.getWorkState()); }
        }
    }
    @Test void executionRequiresPlacementAndUserTrigger() {
        var value=Commitment.create(1L,fields("task","done")); value.establishReadiness();
        assertThrows(CommitmentException.class,()->value.transition("in_progress",false,true));
        assertThrows(CommitmentException.class,()->value.transition("in_progress",true,false));
        value.transition("in_progress",true,true);
        assertThrows(CommitmentException.class,()->value.transition("completed",true,false));
    }
    @Test void deadlineNormalizationAndBounds() {
        assertEquals(Instant.parse("2026-09-20T13:00:00.123456Z"),Commitment.normalize(Instant.parse("2026-09-20T13:00:00.123456789Z")));
        assertNull(Commitment.normalize(null));
        assertThrows(CommitmentException.class,()->Commitment.normalize(Instant.parse("0999-12-31T23:59:59Z")));
        assertThrows(CommitmentException.class,()->Commitment.normalize(Instant.parse("+10000-01-01T00:00:00Z")));
        var converter=new UtcInstantConverter();
        var instant=Instant.parse("2026-09-20T13:00:00.123456Z");
        assertEquals(instant,converter.convertToEntityAttribute(converter.convertToDatabaseColumn(instant)));
    }
    @Test void validatesAllImportanceAndFlexibilityValues() {
        for(String importance:Commitment.IMPORTANCE) for(String flexibility:Commitment.FLEXIBILITY)
            assertDoesNotThrow(()->Commitment.create(1L,new Commitment.Fields(null,null,null,null,null,null,null,importance,flexibility)));
        assertThrows(CommitmentException.class,()->Commitment.create(1L,new Commitment.Fields(null,null,null,null,null,null,null,"urgent","flexible")));
        assertThrows(CommitmentException.class,()->Commitment.create(1L,new Commitment.Fields(null,null,null,null,null,null,null,"high","floating")));
    }
}
