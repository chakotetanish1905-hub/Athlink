package com.examly.springapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConversationMemoryTest {

    @Test
    void storesTurnsOldestFirst() {
        ConversationMemory memory = new ConversationMemory();
        memory.addTurn("s1", "q1", "a1");
        memory.addTurn("s1", "q2", "a2");

        List<ConversationMemory.Turn> history = memory.getHistory("s1");
        assertEquals(2, history.size());
        assertEquals("q1", history.get(0).getUserMessage());
        assertEquals("a2", history.get(1).getBotReply());
    }

    @Test
    void keepsOnlyTheLastEightTurns() {
        ConversationMemory memory = new ConversationMemory();
        for (int i = 1; i <= 10; i++) {
            memory.addTurn("s1", "q" + i, "a" + i);
        }
        List<ConversationMemory.Turn> history = memory.getHistory("s1");
        assertEquals(8, history.size());
        assertEquals("q3", history.get(0).getUserMessage());
        assertEquals("q10", history.get(7).getUserMessage());
    }

    @Test
    void sessionsAreSeparateAndCanBeCleared() {
        ConversationMemory memory = new ConversationMemory();
        memory.addTurn("s1", "q1", "a1");
        memory.addTurn("s2", "other", "answer");

        memory.clear("s1");
        assertTrue(memory.getHistory("s1").isEmpty());
        assertEquals(1, memory.getHistory("s2").size());
        assertTrue(memory.getHistory("unknown").isEmpty());
    }
}
