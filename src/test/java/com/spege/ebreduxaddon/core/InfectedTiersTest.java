package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static com.spege.ebreduxaddon.core.InfectedTiers.ADVANCED;
import static com.spege.ebreduxaddon.core.InfectedTiers.APPRENTICE;
import static com.spege.ebreduxaddon.core.InfectedTiers.MASTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InfectedTiersTest {

    @Test
    void noHivemindNeverMaster() {
        for (double r = 0; r < 1; r += 0.01) {
            assertNotEquals(MASTER, InfectedTiers.maxTier(0, 3, r));
        }
        assertEquals(APPRENTICE, InfectedTiers.maxTier(0, 3, 0.69));
        assertEquals(ADVANCED, InfectedTiers.maxTier(0, 3, 0.70));
    }

    @Test
    void someHiveminds() {
        assertEquals(APPRENTICE, InfectedTiers.maxTier(2, 3, 0.29));
        assertEquals(ADVANCED, InfectedTiers.maxTier(2, 3, 0.30));
        assertEquals(MASTER, InfectedTiers.maxTier(2, 3, 0.80));
    }

    @Test
    void protoWorldNeverApprentice() {
        for (double r = 0; r < 1; r += 0.01) {
            assertNotEquals(APPRENTICE, InfectedTiers.maxTier(3, 3, r));
        }
        assertEquals(MASTER, InfectedTiers.maxTier(9, 3, 0.99));
    }

    @Test
    void brokenInput() {
        assertEquals(APPRENTICE, InfectedTiers.maxTier(-4, 3, Double.NaN));
        assertEquals(MASTER, InfectedTiers.maxTier(1, 0, 5.0));
    }
}
