package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumHullTest {

    /** A sanctum recorded with its hull range, not yet cleansed: the hull is built. */
    @Test
    public void builtForARecordedUncleansedSanctum() {
        assertTrue(SanctumHull.shouldBuild(true, false));
    }

    /**
     * A sanctum recorded before 1.28.0 has no hull range, so nothing could ever dissolve a hull laid
     * into its later-populated chunks: none is built.
     */
    @Test
    public void neverForASanctumWithoutAHullRecord() {
        assertFalse(SanctumHull.shouldBuild(false, false));
        assertFalse(SanctumHull.shouldBuild(false, true));
    }

    /** A chunk populated after the cleanse would get a hull nobody removes. */
    @Test
    public void neverAfterTheCleanse() {
        assertFalse(SanctumHull.shouldBuild(true, true));
    }
}
