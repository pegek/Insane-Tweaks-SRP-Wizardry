package com.spege.insanetweaks.entities.sapling;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SaplingStatsTest {

    @Test
    public void lerpHitsBothEndsAndTheMiddle() {
        assertEquals(50.0D, SaplingStats.lerp(50, 80, 0.0D), 1e-9);
        assertEquals(65.0D, SaplingStats.lerp(50, 80, 0.5D), 1e-9);
        assertEquals(80.0D, SaplingStats.lerp(50, 80, 1.0D), 1e-9);
    }

    @Test
    public void lerpClampsProgress() {
        assertEquals(50.0D, SaplingStats.lerp(50, 80, -1.0D), 1e-9);
        assertEquals(80.0D, SaplingStats.lerp(50, 80, 2.0D), 1e-9);
    }

    @Test
    public void sizeGrowsFromSproutToTwoAndAHalfBlocks() {
        assertEquals(0.6F, SaplingStats.width(0.0D), 1e-6F);
        assertEquals(0.8F, SaplingStats.height(0.0D), 1e-6F);
        assertEquals(1.2F, SaplingStats.width(1.0D), 1e-6F);
        assertEquals(2.5F, SaplingStats.height(1.0D), 1e-6F);
    }

    @Test
    public void syncedProgressRoundTrips() {
        assertEquals(0, SaplingStats.toSynced(0.0D));
        assertEquals(500, SaplingStats.toSynced(0.5D));
        assertEquals(1000, SaplingStats.toSynced(1.0D));
        assertEquals(1000, SaplingStats.toSynced(7.0D));
        assertEquals(0.5D, SaplingStats.fromSynced(500), 1e-9);
    }

    @Test
    public void waveTopsTheAreaUpToBasePlusHalfThePhase() {
        // phase 6: wanted = 4 + 3 = 7; 2 already nearby -> spawn 5
        assertEquals(5, SaplingStats.waveSpawnCount(4, 6, 2, 8));
    }

    @Test
    public void waveSpawnsNothingWhenTheAreaIsAlreadyFull() {
        assertEquals(0, SaplingStats.waveSpawnCount(4, 6, 20, 8));
    }

    @Test
    public void waveNeverExceedsTheCap() {
        assertEquals(8, SaplingStats.waveSpawnCount(4, 10, 0, 8)); // wanted 9
        assertEquals(0, SaplingStats.waveSpawnCount(4, 10, 0, 0));
    }

    @Test
    public void negativePhaseCountsAsZero() {
        assertEquals(4, SaplingStats.waveSpawnCount(4, -3, 0, 8));
    }
}
