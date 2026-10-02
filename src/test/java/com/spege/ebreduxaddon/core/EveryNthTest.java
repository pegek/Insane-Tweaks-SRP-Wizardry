package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveryNthTest {

    @Test
    void firesOnEveryFourth() {
        int counter = 0;
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            EveryNth step = EveryNth.step(counter, 4);
            counter = step.counter();
            pattern.append(step.fires() ? 'X' : '.');
        }
        assertEquals("...X...X", pattern.toString());
    }

    @Test
    void oneFiresEveryTime() {
        assertTrue(EveryNth.step(0, 1).fires());
    }

    @Test
    void disabledAndBrokenState() {
        assertFalse(EveryNth.step(3, 0).fires());
        assertEquals(0, EveryNth.step(3, 0).counter());
        // Stan spoza zakresu (np. obnizone n w configu) zaczyna serie od nowa, zamiast odpalic od razu.
        assertEquals(1, EveryNth.step(99, 4).counter());
        assertEquals(1, EveryNth.step(-7, 4).counter());
    }
}
