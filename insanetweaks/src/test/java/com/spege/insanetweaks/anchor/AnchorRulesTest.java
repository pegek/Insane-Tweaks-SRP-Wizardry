package com.spege.insanetweaks.anchor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnchorRulesTest {

    @Test
    public void callsBelowFraction() {
        assertTrue(AnchorRules.shouldCall(24f, 100f, 0.25D));
        assertFalse(AnchorRules.shouldCall(25f, 100f, 0.25D));
    }

    @Test
    public void lethalHitStillCalls() {
        assertTrue(AnchorRules.shouldCall(-5f, 100f, 0.25D));
    }

    @Test
    public void zeroFractionNeverCalls() {
        assertFalse(AnchorRules.shouldCall(1f, 100f, 0.0D));
        assertFalse(AnchorRules.shouldCall(1f, 0f, 0.25D));
    }

    @Test
    public void artefactChanceBySource() {
        assertEquals(0.2D, AnchorRules.artefactChance(true, true, 0.2D, 0.5D), 1e-9);
        assertEquals(0.5D, AnchorRules.artefactChance(true, false, 0.2D, 0.5D), 1e-9);
        assertEquals(0.0D, AnchorRules.artefactChance(false, false, 0.2D, 0.5D), 1e-9);
        assertEquals(0.0D, AnchorRules.artefactChance(false, true, 0.2D, 0.5D), 1e-9);
    }
}
