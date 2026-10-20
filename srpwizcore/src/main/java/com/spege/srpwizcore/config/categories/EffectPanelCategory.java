package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * The effect panel (2026-10-20): hovering an effect tile in any inventory screen turns it into a
 * short description of what the effect does, read from {@code config/srpwizcore/effect_descriptions.lang}
 * and from the description lang keys other mods already ship. Client-only; a server ignores all of it.
 *
 * <p>Replaces Just Enough Effects' own popup, which is drawn before the inventory and so ends up
 * underneath the very tiles it describes. Spec: {@code docs/superpowers/specs/2026-10-20-effect-panel-design.md}.
 */
public class EffectPanelCategory {

    @Config.Comment({
            "Turn an effect tile into its description while the mouse is over it, in the survival and",
            "creative inventory and any modded screen built the same way. OFF draws the tiles exactly",
            "as vanilla does. Read live, no restart needed. Default ON."
    })
    @Config.Name("Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Size of the description text relative to Minecraft's normal font. Small enough to fit a",
            "few lines into the space of one tile; raise it if it is hard to read at your GUI scale.",
            "Read live, no restart needed. Default 0.6."
    })
    @Config.Name("Font Scale")
    @Config.RangeDouble(min = 0.3, max = 1.0)
    public double fontScale = 0.6;

    @Config.Comment({
            "When nothing describes an effect, show where to add a description: the file and the exact",
            "line to put in it. OFF leaves an undescribed effect's tile alone instead.",
            "Read live, no restart needed. Default ON."
    })
    @Config.Name("Show Missing Hint")
    public boolean showMissingHint = true;

    @Config.Comment({
            "Switch off Just Enough Effects' own description popup, which is drawn underneath the effect",
            "tiles and cannot be read. JEE stays installed: its descriptions are still one of the panel's",
            "sources. Applied once at game start - restart to change. Default ON."
    })
    @Config.Name("Suppress JEE Renderer")
    @Config.RequiresMcRestart
    public boolean suppressJeeRenderer = true;
}
