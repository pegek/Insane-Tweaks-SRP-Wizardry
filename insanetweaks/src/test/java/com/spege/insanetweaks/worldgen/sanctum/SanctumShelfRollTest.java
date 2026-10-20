package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SanctumShelfRollTest {

    @Test
    public void treasureBand() {
        assertEquals(SanctumShelfRoll.TREASURE, SanctumShelfRoll.kind(0.02D, 0.03D, 0.04D));
    }

    @Test
    public void insaneBand() {
        assertEquals(SanctumShelfRoll.INSANE, SanctumShelfRoll.kind(0.05D, 0.03D, 0.04D));
    }

    @Test
    public void none() {
        assertEquals(SanctumShelfRoll.NONE, SanctumShelfRoll.kind(0.07D, 0.03D, 0.04D));
    }

    @Test
    public void zeroChancesNeverHit() {
        assertEquals(SanctumShelfRoll.NONE, SanctumShelfRoll.kind(0.0D, 0.0D, 0.0D));
    }
}
