package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumPalettesTest {

    /** The Overworld mix is exactly the 1.28 wall mix: 60% plain, 25% mossy, 15% cracked. */
    @Test
    public void overworldMixMatchesTheOldWalls() {
        assertEquals("minecraft:stonebrick", SanctumPalettes.wall(0, 0.5D));
        assertEquals("minecraft:stonebrick:1", SanctumPalettes.wall(0, 0.7D));
        assertEquals("minecraft:stonebrick:2", SanctumPalettes.wall(0, 0.9D));
    }

    @Test
    public void dim111IsObsidianAndCryingObsidian() {
        assertEquals("minecraft:obsidian", SanctumPalettes.wall(111, 0.5D));
        assertEquals("nb:cry_obi", SanctumPalettes.wall(111, 0.9D));
        assertEquals("nb:black_stone_bricks", SanctumPalettes.chiseled(111));
        assertEquals("nb:black_stone_brick_stairs", SanctumPalettes.stairs(111));
    }

    @Test
    public void netherAndEndArePrepared() {
        assertEquals("minecraft:nether_brick", SanctumPalettes.wall(-1, 0.5D));
        assertEquals("minecraft:red_nether_brick", SanctumPalettes.wall(-1, 0.9D));
        assertEquals("minecraft:nether_brick_stairs", SanctumPalettes.stairs(-1));
        assertEquals("minecraft:end_bricks", SanctumPalettes.wall(1, 0.5D));
        assertEquals("minecraft:purpur_block", SanctumPalettes.wall(1, 0.9D));
        assertEquals("minecraft:purpur_pillar", SanctumPalettes.chiseled(1));
    }

    @Test
    public void onlyOverworldHasSilverfish() {
        assertTrue(SanctumPalettes.silverfish(0));
        assertFalse(SanctumPalettes.silverfish(111));
        assertFalse(SanctumPalettes.silverfish(-1));
        assertFalse(SanctumPalettes.silverfish(1));
    }

    @Test
    public void unknownDimensionFallsBackToOverworld() {
        assertEquals(SanctumPalettes.wall(0, 0.3D), SanctumPalettes.wall(5, 0.3D));
        assertEquals(SanctumPalettes.chiseled(0), SanctumPalettes.chiseled(5));
        assertEquals(SanctumPalettes.stairs(0), SanctumPalettes.stairs(5));
        assertTrue(SanctumPalettes.silverfish(5));
    }
}
