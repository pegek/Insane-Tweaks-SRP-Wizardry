package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static com.spege.ebreduxaddon.core.EvolutionPick.SCAMPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EvolutionPickTest {

    @Test
    void followsSporesNinetyTenSplit() {
        assertEquals(0, EvolutionPick.choose(0.89, 0.9, 1, 0.5));
        assertEquals(SCAMPER, EvolutionPick.choose(0.90, 0.9, 1, 0.5));
    }

    @Test
    void indexCoversTheWholeList() {
        assertEquals(0, EvolutionPick.choose(0.0, 1.0, 4, 0.0));
        assertEquals(3, EvolutionPick.choose(0.0, 1.0, 4, 0.99));
        assertEquals(3, EvolutionPick.choose(0.0, 1.0, 4, 1.0));
    }

    @Test
    void emptyListIsAlwaysScamper() {
        assertEquals(SCAMPER, EvolutionPick.choose(0.0, 1.0, 0, 0.0));
    }

    @Test
    void chanceIsClamped() {
        assertEquals(0, EvolutionPick.choose(0.999, 5.0, 1, 0.0));
        assertEquals(SCAMPER, EvolutionPick.choose(0.0, -1.0, 1, 0.0));
        assertEquals(SCAMPER, EvolutionPick.choose(0.0, Double.NaN, 1, 0.0));
    }
}
