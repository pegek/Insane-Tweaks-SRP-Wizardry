package com.spege.insanetweaks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FragmentPityTest {

    @Test
    public void aLuckyRollDrops() {
        assertTrue(FragmentPity.drops(0.05D, 0.10D, 0, 15));
    }

    @Test
    public void anUnluckyRollBeforeThePityPointDoesNotDrop() {
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 0, 15));
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 13, 15));
    }

    @Test
    public void theFifteenthEligibleKillIsGuaranteed() {
        // 14 dry kills behind us: this is the 15th.
        assertTrue(FragmentPity.drops(0.99D, 0.10D, 14, 15));
    }

    @Test
    public void pityZeroDisablesTheGuarantee() {
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 1000, 0));
    }

    @Test
    public void counterResetsOnDropAndGrowsOtherwise() {
        assertEquals(0, FragmentPity.nextDryKills(7, true));
        assertEquals(8, FragmentPity.nextDryKills(7, false));
    }

    @Test
    public void simulatedRunNeverGoesFifteenKillsDry() {
        int dry = 0;
        int longestDry = 0;
        for (int kill = 0; kill < 1000; kill++) {
            boolean drop = FragmentPity.drops(0.99D, 0.10D, dry, 15); // always unlucky
            dry = FragmentPity.nextDryKills(dry, drop);
            longestDry = Math.max(longestDry, dry);
        }
        assertEquals(14, longestDry);
    }
}
