package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class HudCategory {

    @Config.Comment({
            "Whether to hide the mana bars other mods draw, so only ManaCore's own bar is shown.",
            "On by default: once ManaCore owns the pool, a second bar shows the same number twice",
            "and is simply confusing. Currently this covers Trinkets and Baubles, the only other",
            "mod in this pack that draws a per-player mana bar; further mods would be added here.",
            "Works live, no restart."})
    public boolean hideForeignManaBars = true;

    @Config.Comment("Whether to draw the mana bar.")
    public boolean showBar = true;

    @Config.Comment("Whether to print the number next to the bar.")
    public boolean showNumber = true;

    @Config.Comment("Horizontal offset of the bar, in GUI pixels.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetX = 0;

    @Config.Comment("Vertical offset of the bar, in GUI pixels.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetY = 0;
}
