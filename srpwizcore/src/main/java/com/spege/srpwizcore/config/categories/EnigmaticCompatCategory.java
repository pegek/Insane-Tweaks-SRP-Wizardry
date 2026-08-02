package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Fixes for Enigmatic Legacy (keletu's 1.12.2 port, {@code enigmaticlegacy-legacy}).
 *
 * <p>One entry so far: the Non-Euclidean Cube's random-destination search. It is recursive with a
 * {@code depth > 10000} bail-out that no JVM thread stack can reach, it runs on the item's own
 * thread pool, and its {@code catch (Exception)} does not catch the {@code StackOverflowError}
 * that ends it — so the failure travels to {@code Future.get()} on the server thread and is
 * rethrown there as a {@code RuntimeException}. Crash 2026-08-02 21:24, {@code Ticking player}.
 *
 * <p>The run of failed probes that gets it there is guaranteed rather than unlucky, because
 * {@code MixinChunkProviderServerThreadGuard} answers that worker with an empty chunk instead of
 * generating one. Full diagnosis and the replacement search:
 * {@code com.spege.srpwizcore.util.CubeLocationSearch}.
 */
public class EnigmaticCompatCategory {

    @Config.Comment({
            "Stops the Non-Euclidean Cube (Enigmatic Legacy) crashing the game, and makes it find",
            "destinations again.",
            "The item looks for somewhere to teleport you by picking random spots and checking the",
            "ground there. It does that by calling itself once per miss, up to ten thousand times",
            "deep - far more than Java can hold - so a run of misses ends in a crash instead of a",
            "shrug. It also does the looking on its own background thread, where this mod stops",
            "terrain from being generated (that protection is what keeps your world save intact),",
            "so out there every spot it checks looks like empty air and every attempt misses.",
            "With this ON the search runs on the main thread, a fixed number of attempts, spread",
            "over several ticks so you do not feel it. If nothing is found you get a safe spot",
            "instead of a crash.",
            "Turning this OFF gives you the mod's own behaviour back, crash included.",
            "No restart needed. Default ON."
    })
    @Config.Name("Fix: Non-Euclidean Cube Location Search")
    public boolean fixCubeLocationSearch = true;

    @Config.Comment({
            "How many free attempts the Cube gets before it starts generating terrain.",
            "These only look at parts of the world that are already loaded, so they cost almost",
            "nothing and usually find nothing either - they are here to catch the lucky hit before",
            "any real work happens.",
            "No restart needed. Default 512."
    })
    @Config.Name("Cube: Loaded-Chunk Probes")
    @Config.RangeInt(min = 0, max = 4096)
    public int cubeLoadedChunkProbes = 512;

    @Config.Comment({
            "How many attempts the Cube gets that are allowed to generate new terrain.",
            "This is the part that actually finds somewhere to go, and the part that costs time,",
            "so keep it low. Raise it if the Cube keeps dropping you at the substitute spot; lower",
            "it if using the Cube causes a noticeable stutter.",
            "No restart needed. Default 8."
    })
    @Config.Name("Cube: Generated-Chunk Probes")
    @Config.RangeInt(min = 0, max = 256)
    public int cubeGeneratedChunkProbes = 8;

    @Config.Comment({
            "How many of those terrain-generating attempts are spent per tick while the Cube looks",
            "for its next destination in the background.",
            "One per tick keeps the search invisible even in a heavy custom dimension. Raising it",
            "finds a destination sooner at the cost of a bigger hitch.",
            "Does not apply when you trigger the Cube's ability yourself - that search has to",
            "answer immediately and spends its whole budget at once.",
            "No restart needed. Default 1."
    })
    @Config.Name("Cube: Generated Probes Per Tick")
    @Config.RangeInt(min = 1, max = 16)
    public int cubeGeneratedProbesPerTick = 1;
}
