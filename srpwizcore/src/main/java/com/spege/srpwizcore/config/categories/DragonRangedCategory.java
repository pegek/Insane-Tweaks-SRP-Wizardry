package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Elemental dragon ranged weapons (2026-08-05): the fire/ice/lightning longbows and crossbows
 * from Spartan Fire and Ice and Fire pass their element to <i>any</i> ammunition, not only to
 * Ice and Fire's dragonbone arrows and Spartan Fire's dragonbone bolts.
 *
 * <p>Spartan Weaponry has no weapon-property mechanism for ranged weapons at all
 * ({@code SpartanWeaponryAPI.createLongbow} takes an {@code IWeaponCallback}, and Spartan Fire
 * passes {@code null}), so the vanilla behaviour is delivered by swapping the spawned projectile
 * for an {@code EntityDragonArrow} — which only happens when the ammunition is a dragon arrow,
 * and never for the dragonsteel tier. This category adds the missing half: the element is
 * attached to whatever projectile the weapon fired.
 *
 * <p>The arrow keeps its own damage and flight physics, so dragonbone ammunition stays the
 * stronger choice (6.0 base damage vs 2.0, and it damages shields).
 */
public class DragonRangedCategory {

    @Config.Comment({
            "Let the elemental dragon longbows, crossbows and bows apply their element to any",
            "ammunition - ordinary arrows included. Without this, only Ice and Fire's dragonbone",
            "arrows and Spartan Fire's dragonbone bolts carry an element, and the dragonsteel",
            "tier has no working ammunition at all.",
            "The arrow itself is not changed: damage, speed and shield behaviour stay whatever the",
            "ammunition already gave. Needs Ice and Fire. Default ON."
    })
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment({
            "Ice and Fire gives its lightning ARROW a 4.0 bonus against dragons, while the matching",
            "bolt and sword both use 6.75. This raises the arrow to 6.75 so all three agree.",
            "Only affects bonus damage dealt to fire and ice dragons - nothing else.",
            "Read live, no restart needed. Default ON."
    })
    @Config.Name("Align Lightning Arrow Bonus")
    public boolean alignLightningArrowBonus = true;

    @Config.Comment({
            "Log every projectile that gets tagged with an element and every element that gets",
            "applied on hit, to latest.log. Use it to verify the feature in game, then turn it off -",
            "it writes a line per shot.",
            "Read live, no restart needed. Default OFF."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;
}
