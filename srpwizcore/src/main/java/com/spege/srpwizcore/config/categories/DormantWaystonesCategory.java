package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/** Dormant-waystone travel system (block, worldgen, channelled teleport, key-item locator). */
public class DormantWaystonesCategory {

    @Config.Comment({"Master switch for the dormant-waystone system (worldgen + teleport +",
            "locator handlers). The block itself stays registered regardless, so disabling",
            "never voids blocks already placed in a world."})
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment({"Registry ID of the key item required to activate a waystone and used as",
            "the locator. Default is Enigmatic Legacy's Dormant Eye."})
    @Config.Name("Key Item")
    public String keyItem = "enigmaticlegacy:enigmatic_eye";

    @Config.Comment({"If true, right-clicking a waystone teleports only while HOLDING the key",
            "item (either hand). Applies to BOTH directions."})
    @Config.Name("Require Key Item")
    public boolean requireKeyItem = true;

    @Config.Comment("Surface-side dimension (worldgen + entry side). Default 0 (Overworld).")
    @Config.Name("Surface Dimension")
    public int dimSurface = 0;

    @Config.Comment("Target-side dimension the waystone travels to. Default 150 (Underneath).")
    @Config.Name("Target Dimension")
    public int dimTarget = 150;

    @Config.Comment("Master switch for natural surface-dimension generation of the waystone.")
    @Config.Name("Worldgen Enabled")
    @Config.RequiresMcRestart
    public boolean worldgenEnabled = true;

    @Config.Comment("Per-chunk probability of a waystone generating (0.0-1.0). Read live.")
    @Config.Name("Worldgen Chance Per Chunk")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double worldgenChancePerChunk = 0.006D;

    @Config.Comment({"Teleport channel time in ticks (20 = 1s). Taking ANY damage during the",
            "channel cancels it. 0 = instant."})
    @Config.Name("Channel Ticks")
    @Config.RangeInt(min = 0, max = 1200)
    public int channelTicks = 40;

    @Config.Comment({"Within this many blocks of the nearest waystone (same dim), the key item's",
            "tooltip shows its exact XYZ. 0 disables the coords line."})
    @Config.Name("Tooltip Coords Range")
    @Config.RangeDouble(min = 0.0D, max = 256.0D)
    public double tooltipCoordsRange = 50.0D;

    @Config.Comment("Particle trail toward the nearest waystone while holding the key item.")
    @Config.Name("Trail Enabled")
    public boolean trailEnabled = true;

    @Config.Comment("Safe-landing column scan ceiling in the target dimension (cave dims: keep under the roof).")
    @Config.Name("Landing Scan Top Y")
    @Config.RangeInt(min = 1, max = 255)
    public int scanTop = 118;

    @Config.Comment("Safe-landing column scan floor.")
    @Config.Name("Landing Scan Bottom Y")
    @Config.RangeInt(min = 1, max = 255)
    public int scanBottom = 5;
}
