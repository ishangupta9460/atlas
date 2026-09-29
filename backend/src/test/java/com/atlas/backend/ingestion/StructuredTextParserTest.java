package com.atlas.backend.ingestion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructuredTextParserTest {
    final DocumentParser parser=new StructuredTextParser();
    @Test void emptyAndSingleLine(){assertTrue(parser.parse(" \n").isEmpty());assertEquals("task",parser.parse("Build a compiler").get(0).type());assertEquals("note",parser.parse("Ambiguous prose").get(0).type());}
    @Test void nestedOrderingAndDuplicateNodesAreDeterministic(){
        String text="# Plan\n## Milestone\n- Build compiler\n  - resource: Guide https://example.com\n- Build compiler\n- optional: Polish\n- prerequisite: Parsing\n- project: Deliver\n- note: Context";
        var nodes=parser.parse(text);assertEquals(9,nodes.size());assertEquals(2,nodes.get(2).parentId());assertEquals(3,nodes.get(3).parentId());assertEquals("resource",nodes.get(3).type());
        assertNotEquals(nodes.get(2).id(),nodes.get(4).id());assertFalse(nodes.get(5).included());assertFalse(nodes.get(6).included());
        for(int i=0;i<20;i++)assertEquals(nodes,parser.parse(text));
    }
    @Test void malformedAndCodeNeverInventTasks(){assertEquals("note",parser.parse("```\n- build secret\n```\n###Malformed").get(0).type());assertEquals("note",parser.parse("###Malformed").get(0).type());}
    @Test void fixedScheduleRequiresAbsoluteDateAndOffset(){
        var parser=new FixedScheduleParser();var nodes=parser.parse("Lecture | 2026-10-01T09:00:00+05:30 | 2026-10-01T10:00:00+05:30\nMonday 9-10 Lecture");
        assertEquals("2026-10-01T03:30:00Z",nodes.get(0).startTime());assertNull(nodes.get(0).warning());assertNull(nodes.get(1).startTime());assertNotNull(nodes.get(1).warning());
    }
}
