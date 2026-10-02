package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastStandTest {

    private static final long COOLDOWN = 1800;

    @Test
    void firesOnFirstDeathWithFullSet() {
        assertTrue(LastStand.fires(true, false, true, 5000, 0, COOLDOWN));
    }

    @Test
    void needsFullSetAndEnabledAndNormalDamage() {
        assertFalse(LastStand.fires(true, false, false, 5000, 0, COOLDOWN));
        assertFalse(LastStand.fires(false, false, true, 5000, 0, COOLDOWN));
        assertFalse(LastStand.fires(true, true, true, 5000, 0, COOLDOWN));
    }

    @Test
    void cooldown() {
        assertFalse(LastStand.fires(true, false, true, 5000 + COOLDOWN - 1, 5000, COOLDOWN));
        assertTrue(LastStand.fires(true, false, true, 5000 + COOLDOWN, 5000, COOLDOWN));
    }

    @Test
    void clockTurnedBackCountsAsCooledDown() {
        assertTrue(LastStand.cooledDown(100, 5000, COOLDOWN));
    }

    @Test
    void immunityWindow() {
        assertTrue(LastStand.immune(5000, 5000, 10));
        assertTrue(LastStand.immune(5009, 5000, 10));
        assertFalse(LastStand.immune(5010, 5000, 10));
        assertFalse(LastStand.immune(4999, 5000, 10));
        assertFalse(LastStand.immune(5000, 0, 10));
    }
}
