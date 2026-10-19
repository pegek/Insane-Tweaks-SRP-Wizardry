package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * DEV TOOL: {@code /devspeed}, a real tick-rate multiplier for speeding up live tests.
 *
 * <p>One flag, and it is a genuine mixin gate (the config declares {@code DevSpeedMixinPlugin}),
 * so OFF means the two patched vanilla classes are not touched at all.
 */
public class DevSpeedCategory {

    @Config.Comment({
            "DEV TOOL - not a gameplay feature. Adds the op-only command /devspeed, which",
            "multiplies the game's tick rate (x0.5 .. x4, 1 = normal) to speed up live tests:",
            "mob spawn saturation, day/night, SRParasites evolution (ticks per game day) and",
            "world-age gates that read getTotalWorldTime() - which /time set does NOT advance.",
            "",
            "It patches the server tick loop (MinecraftServer.run, the four 50 ms constants) and",
            "the client frame timer (Timer.updateTimer) so the client keeps pace. The speed is",
            "NEVER saved: it resets to x1 on every server start and stop and on overworld unload.",
            "The real ceiling is what the server can compute - in a heavy pack (~18-20 ms per",
            "tick) that is about x2-x2.5; '/devspeed' with no argument shows target vs measured.",
            "",
            "Second mode, '/devspeed warp N' (2..200): moves ONLY the clocks (world age + time of",
            "day) N times faster - nearly free, so x50-x100 is fine. Mobs, crops, weather and",
            "per-tick counters stay at normal speed, and it PERMANENTLY ages the save.",
            "'/devspeed warp spawns K' adds up to 4 extra mob-spawn passes per tick while it runs.",
            "",
            "Do not run it together with another tick-rate mod (e.g. TickRate / tickmod) - both",
            "patch the same constants.",
            "",
            "OFF = both classes are left untouched and the command is not registered.",
            "Needs a game restart. Default OFF."
    })
    @Config.Name("Dev Speed: Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = false;
}
