package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Sentient Codex enchantment (loosely after UniqueEnchantments' Grimoire, which
 * only ships on 1.16.5). Sentient Codex raises the level of the other enchantments on the item as
 * its holder feeds it experience.
 *
 * <p>The master on/off switch is {@code ModConfig.modules.enableSentientCodex} (gates the
 * {@code RegistryEvent.Register<Enchantment>} + the Forge-bus handler, hence
 * {@code @Config.RequiresMcRestart} on that flag). The fields below are read live at runtime by
 * {@code SentientCodexHandler}/{@code SentientCodexPool}, so most need no restart.
 *
 * <h3>What replaced the UE formula, and why</h3>
 * 🚨 The boost used to be
 * {@code floor(ln((Start Level + xpLevel * Progression Rate) * 2) * Level Scaling - Step Skip)},
 * read off the holder's <i>current</i> XP level. That had two problems and both are gone:
 * <ul>
 * <li>With the shipped defaults (50 / 0.3 / 0.9 / 5.0) it returned 0 until <b>XP level ~1143</b>.
 *     The enchantment did nothing at all in any realistic game.</li>
 * <li>It rewarded XP the holder happened to be carrying. A player at level 1000 who had never held
 *     the item got the full boost the moment they picked it up.</li>
 * </ul>
 * Sentient Codex now counts only the XP earned <i>while it is in the player's inventory</i>, banked
 * on the item as {@code sentientcodex_fed}, and spends it on steps of geometrically rising cost.
 * Do not reintroduce a term that reads {@code player.experienceLevel}.
 */
public class SentientCodexCategory {

    @Config.Comment({
            "Master live toggle for the Sentient Codex growth EFFECT. When OFF the handler FREEZES: it",
            "stops feeding, stops adding levels, and the starvation penalty goes idle. Levels already",
            "granted stay baked in (no base snapshot is kept to restore). The enchantment stays",
            "registered. Read live (no restart).",
            "To fully remove/unregister the enchantment instead, use modules.enableSentientCodex (restart)."
    })
    @Config.Name("Enchant Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Maximum enchantment level of Sentient Codex itself (unified to 1 - the boost does not",
            "scale with it). Read at registration - requires a MC restart."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 1;

    @Config.Comment({
            "How often (in ticks) the handler banks earned XP, applies growth and checks starvation.",
            "20 = once per second. Lower = snappier but more work. Read live (no restart)."
    })
    @Config.Name("Tick Interval")
    @Config.RangeInt(min = 1, max = 200)
    public int tickInterval = 20;

    // ----------------------------------------------------------------
    // GROWTH
    // ----------------------------------------------------------------

    @Config.Comment({
            "Experience points the FIRST growth step costs. Costs rise geometrically from here, so",
            "this is the knob for how quickly a fresh Codex shows something. 150 XP is roughly the",
            "total a player has banked around level 10. Read live (no restart)."
    })
    @Config.Name("Step Cost Base")
    @Config.RangeInt(min = 1, max = 1000000)
    public int stepCostBase = 150;

    @Config.Comment({
            "How much more each step costs than the one before it: step n costs",
            "  'Step Cost Base' * this^(n-1)   experience points.",
            "At the defaults the three steps cost 150, 255 and 434 XP, i.e. 839 in total. 1.0 makes",
            "every step cost the same. Read live (no restart)."
    })
    @Config.Name("Step Cost Growth")
    @Config.RangeDouble(min = 1.0, max = 10.0)
    public double stepCostGrowth = 1.7;

    @Config.Comment({
            "Hard ceiling on how many growth steps one item can ever reach. Each step adds +1 to",
            "every boostable enchantment, so this is also the most any single enchantment can gain -",
            "the per-enchantment caps below decide which ones stop earlier. Read live (no restart)."
    })
    @Config.Name("Max Steps")
    @Config.RangeInt(min = 0, max = 10)
    public int maxSteps = 3;

    @Config.Comment({
            "Split the XP a player earns evenly between every Codex item they are carrying, instead",
            "of feeding each one the full amount. ON (the default) makes carrying several a real",
            "cost: four Codex items each grow at a quarter speed, so you pick one to raise. Any",
            "remainder from the division is carried over to the next interval, so nothing is lost.",
            "Read live (no restart)."
    })
    @Config.Name("Split XP Between Items")
    public boolean splitXpBetweenItems = true;

    // ----------------------------------------------------------------
    // WHICH ENCHANTMENTS, AND HOW FAR
    // ----------------------------------------------------------------

    @Config.Comment({
            "Skip enchantments whose own max level is 1. Such an enchantment defines no level",
            "scaling, so raising it is at best inert and at worst breaks it - this covers Silk Touch,",
            "Infinity, Flame, Channeling and most modded on/off enchantments without naming any of",
            "them. Read live (no restart)."
    })
    @Config.Name("Skip Single Level Enchantments")
    public boolean skipSingleLevelEnchants = true;

    @Config.Comment({
            "Only raise an enchantment if its own EnumEnchantmentType accepts the item it is sitting",
            "on. Catches enchantments another mod (or a command) pushed onto an item they were never",
            "meant for, which do nothing but would still consume growth. OFF by default because some",
            "mods deliberately hand-roll canApply and leave the type wide. Read live (no restart)."
    })
    @Config.Name("Require Type Match")
    public boolean requireTypeMatch = false;

    @Config.Comment({
            "Enchantments (by registry name) Sentient Codex will not touch. This list ALSO makes the",
            "enchantment incompatible with the Codex on an anvil - the two answers are deliberately",
            "one list so they cannot drift apart.",
            "The defaults are the two Mending variants in this pack: Sentient Codex repairs the item",
            "it lives on, so it is meant to REPLACE Mending, not stack with it.",
            "The old defaults (fortune, efficiency, looting, silk_touch) are gone: silk_touch is",
            "covered by 'Skip Single Level Enchantments', and the other three are now boosted on",
            "purpose. Add them back here if that is too much. Read live (no restart).",
            "🚨 Renamed from 'Excluded Enchantments' because the meaning grew: that key only ever",
            "meant 'do not boost', while this one ALSO bans the combination on an anvil. Forge never",
            "overwrites a key already present in a .cfg, so reusing the name would have silently",
            "turned an existing pack's old list into anvil bans - making Sentient Codex impossible to",
            "put on anything carrying Fortune, Efficiency, Looting or Silk Touch, i.e. any pickaxe."
    })
    @Config.Name("Incompatible Enchantments")
    public String[] excluded = new String[] {
            "minecraft:mending", "somanyenchantments:advancedmending"
    };

    @Config.Comment({
            "When non-empty this becomes a WHITELIST: only these registry names are raised, and every",
            "derived rule above ('Skip Single Level', 'Require Type Match', 'Excluded') is bypassed.",
            "Empty by default. Note the incompatibility check still reads 'Excluded Enchantments'.",
            "Read live (no restart)."
    })
    @Config.Name("Included Enchantments")
    public String[] included = new String[] {};

    @Config.Comment({
            "Levels above its own maximum that a COMMON enchantment may be raised to. Vanilla",
            "Sharpness caps at V, so 3 makes the boosted ceiling Sharpness VIII. Read live."
    })
    @Config.Name("Cap Bonus Common")
    @Config.RangeInt(min = 0, max = 10)
    public int capBonusCommon = 3;

    @Config.Comment({"Same, for UNCOMMON enchantments. Read live."})
    @Config.Name("Cap Bonus Uncommon")
    @Config.RangeInt(min = 0, max = 10)
    public int capBonusUncommon = 3;

    @Config.Comment({"Same, for RARE enchantments (Fortune, Looting). Read live."})
    @Config.Name("Cap Bonus Rare")
    @Config.RangeInt(min = 0, max = 10)
    public int capBonusRare = 3;

    @Config.Comment({
            "Same, for VERY_RARE enchantments. Lowest of the four on purpose: the enchantments that",
            "were hardest to obtain gain the least. Read live."
    })
    @Config.Name("Cap Bonus Very Rare")
    @Config.RangeInt(min = 0, max = 10)
    public int capBonusVeryRare = 1;

    @Config.Comment({
            "Headroom for CURSES, used instead of the rarity bonus above. Curses are raised - the",
            "Codex is a curse itself and does not play favourites - but with the smallest allowance,",
            "so they are the first thing to stop growing. Read live."
    })
    @Config.Name("Cap Bonus Curse")
    @Config.RangeInt(min = 0, max = 10)
    public int capBonusCurse = 1;

    // ----------------------------------------------------------------
    // STARVATION
    // ----------------------------------------------------------------

    @Config.Comment({
            "Punish a holder who stops feeding the Codex. Levels already granted are never taken",
            "back - the penalty falls on the player, not the item. Read live (no restart)."
    })
    @Config.Name("Starvation Enabled")
    public boolean starvationEnabled = true;

    @Config.Comment({
            "How long the Codex tolerates receiving no experience before it starts to complain, in",
            "ticks. 24000 = one Minecraft day. Read live (no restart)."
    })
    @Config.Name("Starvation Grace Ticks")
    @Config.RangeInt(min = 0, max = 432000)
    public int starvationGraceTicks = 24000;

    @Config.Comment({
            "Ticks of continued fasting per severity step past the grace period. 6000 = 5 minutes.",
            "Severity starts at 1 the moment the grace period ends. Read live (no restart)."
    })
    @Config.Name("Starvation Escalation Ticks")
    @Config.RangeInt(min = 20, max = 432000)
    public int starvationEscalationTicks = 6000;

    @Config.Comment({
            "Highest severity the penalty can reach, so a forgotten Codex in the bottom of a pack",
            "cannot escalate without limit. Read live (no restart)."
    })
    @Config.Name("Max Starvation Severity")
    @Config.RangeInt(min = 1, max = 20)
    public int maxStarvationSeverity = 4;

    @Config.Comment({
            "MAGIC damage dealt per severity step, once per tick interval. 0 disables the damage and",
            "leaves the hunger drain and debuffs. Read live (no restart)."
    })
    @Config.Name("Starvation Damage")
    @Config.RangeDouble(min = 0.0, max = 100.0)
    public double starvationDamage = 1.0;

    @Config.Comment({
            "Exhaustion added per severity step, once per tick interval - the Codex eating into the",
            "hunger bar. 4.0 exhaustion costs one haunch. 0 disables it. Read live (no restart)."
    })
    @Config.Name("Starvation Exhaustion")
    @Config.RangeDouble(min = 0.0, max = 40.0)
    public double starvationExhaustion = 0.5;

    @Config.Comment({
            "Apply Weakness and Mining Fatigue while starving, at an amplifier of (severity - 1).",
            "Read live (no restart)."
    })
    @Config.Name("Starvation Debuffs")
    public boolean starvationDebuffs = true;

    // ----------------------------------------------------------------
    // REPAIR (the Mending role)
    // ----------------------------------------------------------------

    @Config.Comment({
            "Durability restored to the Codex item when its wielder kills something with it. This is",
            "why the Codex is incompatible with Mending: it does the same job, paid for in blood",
            "rather than in experience. 0 disables it. Read live (no restart)."
    })
    @Config.Name("Repair Per Kill")
    @Config.RangeInt(min = 0, max = 10000)
    public int repairPerKill = 10;

    @Config.Comment({
            "Durability restored per point of damage the wielder deals with the item. 0 (the default)",
            "disables it and leaves repair entirely to kills. Read live (no restart)."
    })
    @Config.Name("Repair Per Damage Dealt")
    @Config.RangeDouble(min = 0.0, max = 10.0)
    public double repairPerDamageDealt = 0.0;

    @Config.Comment({
            "Spread the repair across worn armour carrying the Codex as well, not just the two hands.",
            "🚨 Default ON, and turning it off is a real decision: 'Block Anvil Modification' refuses",
            "every anvil operation on a Codex item INCLUDING material repair, and Mending is on the",
            "exclusion list, so this repair is the only way Codex armour ever recovers durability.",
            "With this OFF a Codex chestplate will eventually break for good. Read live (no restart)."
    })
    @Config.Name("Repair Armor Too")
    public boolean repairArmorToo = true;

    // ----------------------------------------------------------------
    // MISC
    // ----------------------------------------------------------------

    @Config.Comment({
            "When ON, a Sentient Codex item ALSO carries the 'Ashen Legacy' property for free: once dropped",
            "it is routed through the hardened EntityItemIndestructible (immune to fire, lava, cactus and",
            "explosions) and lingers far longer than ordinary gear.",
            "Default is now OFF: the two are separate rewards since 1.4.21. Ashen Legacy is applied with",
            "its own Property Book on an anvil, so an item can have either, both, or neither. Turn this",
            "back ON to restore the old bundled behaviour. Read live (no restart)."
    })
    @Config.Name("Confer Ashen Legacy")
    public boolean conferAshenLegacy = false;

    @Config.Comment({
            "Safety net for the split above. When ON and 'Confer Ashen Legacy' is OFF, every Sentient",
            "Codex item a player already owns is granted Ashen Legacy once, permanently, the first time",
            "they log in - so nothing that was already lava-proof silently stops being lava-proof.",
            "Runs exactly once per player and covers inventory, armour, offhand and ender chest. Items",
            "sitting in a chest are not reached; they keep whatever they were explicitly granted.",
            "Newly enchanted items are NOT covered - that is the point of the split. Read live."
    })
    @Config.Name("Grandfather Ashen Legacy")
    public boolean grandfatherAshenLegacy = true;

    @Config.Comment({
            "When ON, an item that already carries Sentient Codex cannot be further modified on an anvil.",
            "Applying the Sentient Codex book onto a clean item is still allowed. Read live (no restart)."
    })
    @Config.Name("Block Anvil Modification")
    public boolean blockAnvil = true;
}
