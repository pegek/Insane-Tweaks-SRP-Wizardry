package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorBonusTest {

    @Test
    void piecesAddUp() {
        assertEquals(1.0, ArmorBonus.costMultiplier(0, 0, 0.03, 0.05), 1e-9);
        assertEquals(0.84, ArmorBonus.costMultiplier(2, 2, 0.03, 0.05), 1e-9);
        assertEquals(1.06, ArmorBonus.potencyMultiplier(2, 0.03), 1e-9);
    }

    @Test
    void floorAndBrokenConfig() {
        assertEquals(0.5, ArmorBonus.costMultiplier(4, 0, 0.9, 0), 1e-9);
        assertEquals(1.0, ArmorBonus.costMultiplier(4, 4, Double.NaN, -1), 1e-9);
        assertEquals(1.0, ArmorBonus.potencyMultiplier(-3, 0.03), 1e-9);
    }
}
