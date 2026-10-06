package com.spege.insanetweaks.entities.sapling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SaplingDefensePlanTest {

    /** The shipped defaults: 60 s lure, 4 waves, 105 s apart. */
    private final SaplingDefensePlan plan = new SaplingDefensePlan(60, 4, 105);

    @Test
    public void totalIsLurePlusAllWaveIntervals() {
        assertEquals((60 + 4 * 105) * 20, plan.totalTicks());
    }

    @Test
    public void wavesStartAfterTheLureOneIntervalApart() {
        assertEquals(60 * 20, plan.waveStartTick(1));
        assertEquals(165 * 20, plan.waveStartTick(2));
        assertEquals(270 * 20, plan.waveStartTick(3));
        assertEquals(375 * 20, plan.waveStartTick(4));
    }

    @Test
    public void noWaveIsDueDuringTheLure() {
        assertEquals(0, plan.wavesDueBy(0));
        assertEquals(0, plan.wavesDueBy(60 * 20 - 1));
    }

    @Test
    public void aWaveIsDueExactlyAtItsStartTick() {
        assertEquals(1, plan.wavesDueBy(60 * 20));
        assertEquals(1, plan.wavesDueBy(165 * 20 - 1));
        assertEquals(2, plan.wavesDueBy(165 * 20));
        assertEquals(4, plan.wavesDueBy(375 * 20));
    }

    @Test
    public void wavesDueNeverExceedsTheWaveCount() {
        assertEquals(4, plan.wavesDueBy(plan.totalTicks()));
        assertEquals(4, plan.wavesDueBy(plan.totalTicks() * 3));
    }

    @Test
    public void progressIsClampedToZeroAndOne() {
        assertEquals(0.0D, plan.progress(-100), 1e-9);
        assertEquals(0.5D, plan.progress(plan.totalTicks() / 2), 1e-9);
        assertEquals(1.0D, plan.progress(plan.totalTicks() + 100), 1e-9);
    }

    @Test
    public void completeOnlyAtTheEnd() {
        assertFalse(plan.isComplete(plan.totalTicks() - 1));
        assertTrue(plan.isComplete(plan.totalTicks()));
    }

    @Test
    public void legacyGrowthMapsProportionallyOntoTheNewTimeline() {
        assertEquals(0, plan.migrateLegacyGrowth(0));
        assertEquals(plan.totalTicks() / 2, plan.migrateLegacyGrowth(SaplingDefensePlan.LEGACY_TOTAL_TICKS / 2));
    }

    @Test
    public void legacyGrowthNeverMigratesStraightIntoBloom() {
        assertEquals(plan.totalTicks() - 20, plan.migrateLegacyGrowth(SaplingDefensePlan.LEGACY_TOTAL_TICKS));
        assertEquals(plan.totalTicks() - 20, plan.migrateLegacyGrowth(Integer.MAX_VALUE));
        assertEquals(0, plan.migrateLegacyGrowth(-5));
    }

    @Test
    public void nonsenseConfigIsClampedToAWorkingPlan() {
        SaplingDefensePlan broken = new SaplingDefensePlan(-1, 0, 0);
        assertEquals(1, broken.waveCount());
        assertEquals(20, broken.totalTicks());
        assertEquals(1, broken.wavesDueBy(0));
    }
}
