package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Open Terrain Generator (OTG) compatibility module.
 *
 * <p>OTG dimensions that use virtual biomes (all biomes defined via ReplaceToBiomeName, e.g. the
 * Underneath dim 150) can return {@code null} from {@code ForgeWorld.getBiome(x,z)} when the
 * internal OTG id lookup misses the registered pool. Two structure generators — Nether Fortress
 * and Mineshaft — dereference this null without a guard, crashing the server with
 * {@code NullPointerException: Cannot invoke "LocalBiome.getBiomeConfig()"}.
 *
 * <p>The mixin fixes live in {@code mixins.insanetweaks.otg.json}, which {@code LateMixinBooter}
 * only queues when {@code openterraingenerator} is present. The individual handler additionally
 * self-gates on the flag below at HEAD, so toggling it off makes the mixin behave exactly like
 * unmodified OTG.
 */
public class OtgCompatCategory {

    @Config.Comment({
            "Fix OTG structure generators (Nether Fortress, Mineshaft) crashing with NPE",
            "when the biome lookup returns null (virtual biomes in OTG dimensions like Underneath).",
            "When true, the structure's canSpawnStructureAtCoords returns false for null biomes",
            "instead of crashing. Requires MC restart (mixin gate). Default ON."
    })
    @Config.Name("Fix: OTG Structure Gen Null Biome")
    @Config.RequiresMcRestart
    public boolean fixStructureGenNullBiome = true;
}
