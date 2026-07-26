package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Auto Lock Picker item (integrates with the optional Locks mod). The master
 * on/off switch is {@code ModConfig.modules.enableAutoLockPicker}; the item itself is always
 * registered, so toggling that flag never removes a registry entry from an existing world.
 *
 * <p>Every difficulty knob scales off the lock's <b>pin count</b> ({@code Lock.getLength()}) rather
 * than off the specific lock item, so it keeps working for any lock a future version of Locks (or
 * an addon) adds. Stock pin counts: wood 5, gold 6, iron 7, steel 9, diamond 11.
 *
 * <p>Accessed as {@code ModConfig.autoLockPicker.*}.
 */
public class AutoLockPickerCategory {

    @Config.Comment({
            "Flat tick cost added to every channel, on top of the per-pin cost. 20 = one second.",
            "Read live (no restart)."
    })
    @Config.Name("Base Channel Ticks")
    @Config.RangeInt(min = 0, max = 6000)
    public int baseChannelTicks = 20;

    @Config.Comment({
            "Ticks of channelling added per pin in the lock. With the default 20 the stock locks take",
            "wood 6s / gold 7s / iron 8s / steel 10s / diamond 12s. Read live (no restart)."
    })
    @Config.Name("Ticks Per Pin")
    @Config.RangeInt(min = 0, max = 600)
    public int ticksPerPin = 20;

    @Config.Comment({
            "Durability consumed per pin on a SUCCESSFUL pick (diamond lock = 11 pins = 11 durability).",
            "An aborted channel costs nothing. Vanilla Unbreaking applies on top automatically, because",
            "the cost goes through ItemStack.damageItem. Read live (no restart)."
    })
    @Config.Name("Durability Per Pin")
    @Config.RangeInt(min = 0, max = 100)
    public int durabilityPerPin = 1;

    @Config.Comment({
            "Total durability of the Auto Lock Picker. 250 is roughly 22 diamond locks without Unbreaking.",
            "Served live through Item#getMaxDamage(ItemStack), so no restart is needed. Existing items",
            "keep their damage value, they just gain or lose headroom."
    })
    @Config.Name("Max Durability")
    @Config.RangeInt(min = 1, max = 100000)
    public int maxDurability = 250;

    @Config.Comment({
            "The picker's equivalent of a lock pick's 'strength', used ONLY for the Complexity gate:",
            "Locks allows picking iff strength > Complexity level * 0.25. At the default 0.7 the picker",
            "beats Complexity I and II and is stopped by Complexity III - the same bracket as the steel",
            "lock pick. Locks' own picks: wood 0.2, gold 0.25, iron 0.35, steel 0.7, diamond 0.85.",
            "Read live (no restart)."
    })
    @Config.Name("Pick Strength")
    @Config.RangeDouble(min = 0.0, max = 10.0)
    public double pickStrength = 0.7D;

    @Config.Comment({
            "Fraction of the channel time removed per level of the Swift Picking enchantment.",
            "0.15 = -15%/level, so level III channels at 55% of the base time. Read live (no restart)."
    })
    @Config.Name("Swift Picking Reduction Per Level")
    @Config.RangeDouble(min = 0.0, max = 0.33)
    public double swiftReductionPerLevel = 0.15D;

    @Config.Comment({
            "When ON, a lock enchanted with Locks' Complexity can be too complex for the picker",
            "(see 'Pick Strength'). OFF makes the picker ignore Complexity entirely. Read live."
    })
    @Config.Name("Respect Complexity")
    public boolean respectComplexity = true;

    @Config.Comment({
            "When ON, Locks' Sturdy enchantment on the lock raises the durability cost instead of",
            "(as in Locks' own minigame) risking a broken pick. Read live (no restart)."
    })
    @Config.Name("Apply Sturdy Durability Cost")
    public boolean applySturdyDurability = true;

    @Config.Comment({
            "Extra durability cost per Sturdy level, as a fraction of the base cost. 0.5 = +50%/level,",
            "so Sturdy III on a diamond lock costs 11 * 2.5 = 27. Read live (no restart)."
    })
    @Config.Name("Sturdy Durability Per Level")
    @Config.RangeDouble(min = 0.0, max = 10.0)
    public double sturdyDurabilityPerLevel = 0.5D;

    @Config.Comment({
            "When ON, aborting a channel on a lock enchanted with Locks' Shocking zaps the player.",
            "Only a player-initiated abort counts: if the channel is cut short because the lock was",
            "opened or removed by someone else, there is no shock. Read live (no restart)."
    })
    @Config.Name("Apply Shocking On Interrupt")
    public boolean applyShockingOnInterrupt = true;

    @Config.Comment({
            "Damage per Shocking level dealt on an aborted channel. Read live (no restart)."
    })
    @Config.Name("Shock Damage Per Level")
    @Config.RangeDouble(min = 0.0, max = 100.0)
    public double shockDamagePerLevel = 1.0D;

    @Config.Comment({
            "Maximum distance in blocks between the player and the lock's centre for the channel to",
            "keep running. Walking further aborts it (with no durability cost). Read live (no restart)."
    })
    @Config.Name("Max Channel Distance")
    @Config.RangeDouble(min = 1.0, max = 64.0)
    public double maxChannelDistance = 6.0D;

    @Config.Comment({
            "Enchantability of the Auto Lock Picker at the enchanting table (iron tools are 14).",
            "Served live through Item#getItemEnchantability(). Read live (no restart)."
    })
    @Config.Name("Enchantability")
    @Config.RangeInt(min = 0, max = 100)
    public int enchantability = 14;

    @Config.Comment({
            "Client-only: draw the channel progress bar above the hotbar while picking.",
            "Read live (no restart)."
    })
    @Config.Name("Show Progress Bar")
    public boolean showProgressBar = true;
}
