package com.spege.srpwizcore.config.categories;

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
            "Stops the server crashing while generating terrain in an OpenTerrainGenerator dimension.",
            "In OTG dimensions whose biomes are all remapped to something else, OTG can fail to work",
            "out which biome a spot belongs to. Nether fortress and mineshaft generation do not expect",
            "that and crash the server outright.",
            "With this ON, those two generators simply place no structure there instead of crashing.",
            "Does nothing unless OpenTerrainGenerator is installed. Requires a restart. Default ON."
    })
    @Config.Name("Fix: OTG Structure Gen Null Biome")
    @Config.RequiresMcRestart
    public boolean fixStructureGenNullBiome = true;

    @Config.Comment({
            "Hide OpenTerrainGenerator's 'OTG' entry from the world-type button on the Create World",
            "screen.",
            "Useful for a pack that injects its OTG dimensions into a normal Overworld and does not",
            "want players picking the OTG world type by accident. Worlds already created with it still",
            "load normally.",
            "Only enable this if your pack is set up that way - on a plain OTG setup it hides an option",
            "players need. Does nothing unless OTG is installed.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Hide: OTG World Type In Create Menu")
    public boolean hideOtgWorldType = false;
}
