package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Mmmm enchantment (native port of UniqueEnchantments' Ambrosia): eating enchanted
 * food fills the hunger bar and grants the Nourished effect for a short, fixed duration.
 *
 * <p>🚨 The duration and the Nourished amplifier depend on the <b>enchantment level and nothing
 * else</b>. Upstream Ambrosia scaled both off the eater's XP level through
 * {@code BASE + ln(5^(1 + xpLevel * level)) * MULTIPLIER}, which the port inherited without anyone
 * choosing it: it made a level-30 player sated for nearly four minutes and a level-100 player for
 * eleven, with no cap. That whole curve is gone, along with the tier-strength machinery that only
 * existed to soften it. Do not reintroduce an XP term here.
 *
 * <p>Accessed as {@code ModConfig.enchantments.mmmm.*}. The master toggle is
 * {@code modules.enableMmmm}.
 */
public class MmmmCategory {

    @Config.Comment({
            "Maximum level of Mmmm. Each level is worth 'Duration Per Level Ticks' more Nourished",
            "time and one more amplifier step, so the tiers differ only in those two numbers.",
            "Read at registration - requires a MC restart."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 2;

    @Config.Comment({
            "Duration of the Nourished effect at level I, in ticks. 200 ticks = 10 seconds.",
            "Read live.",
            "(Renamed from 'Base Duration Ticks', which meant something else: the flat part added to",
            "an XP-scaled curve that no longer exists. Forge never overwrites a key already present",
            "in a .cfg, so keeping the old name would have left every existing pack on the old 600.)"
    })
    @Config.Name("Level I Duration Ticks")
    @Config.RangeInt(min = 0, max = 72000)
    public int baseDurationTicks = 200;

    @Config.Comment({
            "Extra Nourished ticks for every level above the first: the effect lasts",
            "  'Base Duration Ticks' + (level - 1) * this   ticks.",
            "At the defaults that is 200 ticks (10s) at level I and 300 (15s) at level II. The",
            "amplifier is (level - 1), which vanilla draws one higher, so level I reads",
            "'Nourished I' and level II 'Nourished II'. Read live."
    })
    @Config.Name("Duration Per Level Ticks")
    @Config.RangeInt(min = 0, max = 72000)
    public int durationPerLevelTicks = 100;

    @Config.Comment({
            "Fill the hunger bar completely the moment the enchanted food is eaten, on top of what",
            "the food itself restores. This is upstream behaviour and the main reason to want the",
            "enchantment now that Nourished is short; turn it off to leave the food's own healing",
            "alone and let Nourished do all the work. Read live."
    })
    @Config.Name("Fill Hunger Bar")
    public boolean fillHungerBar = true;

    @Config.Comment({
            "Make Mmmm-enchanted food immune to Scape and Run: Parasites' food contamination - the",
            "hit that eats part of a food stack and drops Infected Flesh on the ground in its place.",
            "Enchanted stacks become invisible to that scan, so the parasite simply moves on to the",
            "next food stack in the inventory; it does not stop the contamination outright. Requires",
            "SRParasites, and does nothing while modules.enableMmmm is off. Read live."
    })
    @Config.Name("Protect Food From Parasite Contamination")
    public boolean protectFromParasiteContamination = true;

    @Config.Comment({
            "Protect the enchantment's CARRIER - the food stack it sits on - from being swapped for",
            "one of the 'Forbidden Carriers' below by any other mod interaction. This is the general",
            "form of 'Protect Food From Parasite Contamination' above, which only covers the one SRP",
            "vector we could name; this one covers whatever else turns your food into rot.",
            "Does nothing while modules.enableMmmm is off. Read live."
    })
    @Config.Name("Protect Carrier From Swap")
    public boolean protectCarrierFromSwap = true;

    @Config.Comment({
            "Items Mmmm refuses to be carried by, as registry names. A stack of one of these that is",
            "found carrying Mmmm gets turned back into the food it came from when we know it, and",
            "otherwise has the enchantment stripped off it - either way the enchantment never ends up",
            "owned by a rot item. These items also stop accepting Mmmm on an anvil.",
            "Format: modid:path, or modid:path#meta to pin a single metadata value.",
            "Add e.g. srparasites:infected_drop here to cover SRP's Infected Flesh too.",
            "Empty = the guard is inert. Read live."
    })
    @Config.Name("Forbidden Carriers")
    public String[] forbiddenCarriers = { "minecraft:rotten_flesh" };

    @Config.Comment({
            "Log one INFO line the first time the carrier guard catches each item at each route.",
            "Diagnostic: use it to find out WHICH interaction is trying to rot your enchanted food,",
            "so it can be fixed at the source. Deduplicated, so it will not flood the log. Read live."
    })
    @Config.Name("Log Carrier Guard")
    public boolean logCarrierGuard = false;
}
