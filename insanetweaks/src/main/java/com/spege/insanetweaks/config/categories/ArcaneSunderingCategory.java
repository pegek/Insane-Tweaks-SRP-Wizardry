package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Arcane Sundering: the Sentient Spellblade's reward at 1900 kills, in the slot Fleshbound used to
 * occupy there.
 *
 * <p>The two shares <b>partition</b> the hit rather than adding to it: with the defaults, a strike
 * lands as 80% physical, 10% true and 10% magic, so the weapon's listed attack damage still means
 * what it says against an unarmoured target. What changes is how much of it survives armour - the
 * true share answers to nothing, and the magic share slips past armour points while still being
 * reduced by Resistance and Protection.
 *
 * <p>The percentages cap at 45 each on purpose. The physical remainder must stay above zero:
 * {@code EntityLivingBase.damageEntity} returns early on a non-positive amount, and
 * {@code LivingDamageEvent} - where both shares are re-added - would then never fire, silently
 * deleting the whole hit rather than redistributing it.
 */
public class ArcaneSunderingCategory {

    @Config.Comment({"Master switch for the Arcane Sundering property. Read live: the handler",
            "registers regardless and checks this per hit."})
    @Config.Name("Enable Arcane Sundering")
    public boolean enabled = true;

    @Config.Comment({"Percent of each melee hit re-dealt as true damage - ignores armour, Resistance,",
            "Protection and absorption alike. Taken out of the physical portion, not added on top.",
            "Read live."})
    @Config.Name("True Damage Percent")
    @Config.RangeInt(min = 0, max = 45)
    public int trueDamagePercent = 10;

    @Config.Comment({"Percent of each melee hit converted from physical to magic damage. Bypasses",
            "armour points, but is still reduced by Resistance and by Protection enchantments,",
            "exactly as vanilla magic damage is. Taken out of the physical portion. Read live."})
    @Config.Name("Magic Damage Percent")
    @Config.RangeInt(min = 0, max = 45)
    public int magicDamagePercent = 10;
}
