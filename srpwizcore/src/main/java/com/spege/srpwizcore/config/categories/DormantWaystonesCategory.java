package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/** Dormant-waystone travel system (block, worldgen, channelled teleport, key-item locator). */
public class DormantWaystonesCategory {

    @Config.Comment({"Adds Dormant Waystones: rare structures that generate on the surface and",
            "carry you to another dimension and back, one paired waystone at a time.",
            "This adds worldgen and a travel route, so it is off by default - set the two dimensions",
            "and the key item below to match your world before switching it on.",
            "The block itself always stays registered, so turning this off never destroys waystones",
            "already placed in a world. Requires a restart. Default OFF."})
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = false;

    @Config.Comment({"The item a player must be holding to use a waystone, and which points them",
            "toward the nearest one. Written as a registry id.",
            "The default is Enigmatic Legacy's Dormant Eye - change it if you do not have that mod."})
    @Config.Name("Key Item")
    public String keyItem = "enigmaticlegacy:enigmatic_eye";

    @Config.Comment({"Require the key item in hand to travel. Applies in both directions.",
            "Turn this off to let anyone use a waystone they find."})
    @Config.Name("Require Key Item")
    public boolean requireKeyItem = true;

    @Config.Comment("The dimension waystones generate in and travel from. 0 is the Overworld.")
    @Config.Name("Surface Dimension")
    public int dimSurface = 0;

    @Config.Comment({"The dimension waystones travel to. The default (150) is the pack this was",
            "written for - set it to whichever dimension you want to link."})
    @Config.Name("Target Dimension")
    public int dimTarget = 150;

    @Config.Comment("Whether waystones generate naturally at all. Turn off to place them by hand only.")
    @Config.Name("Worldgen Enabled")
    @Config.RequiresMcRestart
    public boolean worldgenEnabled = true;

    @Config.Comment({"How likely a waystone is per chunk, from 0.0 to 1.0.",
            "The default 0.006 works out to roughly one waystone every 170 chunks. No restart needed."})
    @Config.Name("Worldgen Chance Per Chunk")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double worldgenChancePerChunk = 0.006D;

    @Config.Comment({"How long you must hold the waystone before it takes you, in ticks (20 = 1",
            "second). Taking any damage cancels it. 0 = travel instantly."})
    @Config.Name("Channel Ticks")
    @Config.RangeInt(min = 0, max = 1200)
    public int channelTicks = 40;

    @Config.Comment({"Within this many blocks of the nearest waystone, the key item's tooltip shows",
            "its exact coordinates. 0 hides the coordinates entirely."})
    @Config.Name("Tooltip Coords Range")
    @Config.RangeDouble(min = 0.0D, max = 256.0D)
    public double tooltipCoordsRange = 50.0D;

    @Config.Comment("Show a trail of particles toward the nearest waystone while holding the key item.")
    @Config.Name("Trail Enabled")
    public boolean trailEnabled = true;

    @Config.Comment({"Highest Y the game will look at when finding somewhere safe to put you down.",
            "In a cave dimension with a solid roof, keep this below the roof."})
    @Config.Name("Landing Scan Top Y")
    @Config.RangeInt(min = 1, max = 255)
    public int scanTop = 118;

    @Config.Comment("Lowest Y the game will look at when finding somewhere safe to put you down.")
    @Config.Name("Landing Scan Bottom Y")
    @Config.RangeInt(min = 1, max = 255)
    public int scanBottom = 5;
}
