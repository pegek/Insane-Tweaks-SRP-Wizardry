package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Future MC (futuremc) native-patch compatibility module.
 *
 * <p>The bamboo mixin lives in {@code mixins.insanetweaks.futuremc.json}, which
 * {@code LateMixinBooter} only queues when {@code futuremc} is present. The handler
 * additionally self-gates on the flag below, so toggling it off makes bamboo worldgen
 * behave exactly like unmodified FutureMC (crash included).
 */
public class FutureMcCompatCategory {

    @Config.Comment({
            "Guard FutureMC's bamboo worldgen against the OTG-populate race crash.",
            "BambooWorldGen.generate places a bamboo block then calls grow(); grow() reads",
            "world.getBlockState(pos.up(n)).getValue(MATURE) assuming bamboo, but during OTG",
            "chunk population that block above the stalk can already be air, throwing",
            "'Cannot get property PropertyBool{mature} ... block=minecraft:air' and crashing",
            "chunk gen (crash-2026-07-21_06.26.53, seen in dim 150 jungle-wrapper biomes).",
            "With this ON, the grow() call is wrapped so that IllegalArgumentException skips",
            "that one bamboo placement instead of crashing. Bamboo otherwise generates and",
            "grows exactly as vanilla FutureMC. This is what lets futuremc bamboo Enabled=true",
            "be safe again. Read live at call time (no restart). Default ON."
    })
    @Config.Name("Guard: Bamboo Worldgen Race")
    public boolean guardBambooWorldgenRace = true;
}
