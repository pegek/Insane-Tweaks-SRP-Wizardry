package com.spege.srpwizcore.config.categories;

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
            "Stops FutureMC's bamboo crashing terrain generation.",
            "When bamboo is placed while the surrounding chunk is still being built, it can try to",
            "grow into a block that is not there yet and take the whole chunk generation down with it.",
            "It shows up as a crash mentioning 'Cannot get property mature'.",
            "With this ON that one bamboo stalk is skipped instead. Bamboo otherwise generates and",
            "grows exactly as normal - this is what makes it safe to leave bamboo enabled in FutureMC.",
            "Does nothing unless FutureMC is installed. No restart needed. Default ON."
    })
    @Config.Name("Guard: Bamboo Worldgen Race")
    public boolean guardBambooWorldgenRace = true;
}
