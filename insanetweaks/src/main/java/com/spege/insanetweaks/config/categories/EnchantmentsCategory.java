package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Top-level container for the mod's custom-enchantment tunables. Each enchantment gets its own
 * nested block (Forge recurses into public object fields, mirroring how {@code EntitiesCategory}
 * nests {@code assimilatedWizard}), plus the flags that keep all of them out of natural sources.
 *
 * <p>Accessed as {@code ModConfig.enchantments.sentientCodex.*} /
 * {@code ModConfig.enchantments.blockNaturalDiscovery}.
 */
public class EnchantmentsCategory {

    @Config.Name("sentientCodex")
    @Config.Comment("Sentient Codex enchantment (native port of UniqueEnchantments' Grimoire): boost formula, "
            + "per-enchant cap, owner-binding, drop/anvil protection. Master toggle is modules.enableSentientCodex.")
    public final SentientCodexCategory sentientCodex = new SentientCodexCategory();

    @Config.Name("swiftPicking")
    @Config.Comment("Swift Picking enchantment: max level and enchanting-table cost. It shortens the Auto Lock "
            + "Picker's channel; the per-level reduction itself lives in autoLockPicker.swiftReductionPerLevel.")
    public final SwiftPickingCategory swiftPicking = new SwiftPickingCategory();

    @Config.Name("mmmm")
    @Config.Comment("Mmmm enchantment (native port of UniqueEnchantments' Ambrosia): food only. Eating an "
            + "enchanted stack fills hunger and grants Nourished, whose duration scales with XP level. "
            + "Master toggle is modules.enableMmmm.")
    public final MmmmCategory mmmm = new MmmmCategory();

    @Config.Comment({
            "Keep EVERY enchantment registered by this mod out of all natural/random sources:",
            "generated chest loot (dungeons, mineshafts, fishing treasure...), librarian enchanted-book",
            "trades, the enchanting table, and randomly enchanted mob equipment. They are meant to be",
            "handed out deliberately - a quest reward, a command, or a loot table that names the",
            "enchantment explicitly - and then applied on an anvil, all of which keep working.",
            "The rule is by namespace: a future insanetweaks enchantment is protected automatically,",
            "with no list to update. Use 'Natural Discovery Exceptions' to put one back in circulation.",
            "Read live on every roll - no MC restart needed. NOTE: the mixins that enforce this are",
            "applied unconditionally (their config declares no plugin), so this flag is an early-return",
            "inside the handlers rather than a mixin-application gate."
    })
    @Config.Name("Block Natural Discovery")
    public boolean blockNaturalDiscovery = true;

    @Config.Comment({
            "Enchantments exempted from 'Block Natural Discovery', i.e. allowed back into random loot,",
            "villager trades and the enchanting table. Accepts either the full registry name",
            "(insanetweaks:swift_picking) or just the path (swift_picking); case-insensitive.",
            "Only insanetweaks enchantments are ever blocked, so listing anything else has no effect.",
            "Read live.",
            "NOTE: an exemption listed here still will not appear at the enchanting table, because the",
            "table is additionally blocked in Java by EnchantmentInsaneTweaksBase (treasure flag +",
            "canApplyAtEnchantingTable) - that layer is deliberately independent of this config."
    })
    @Config.Name("Natural Discovery Exceptions")
    public String[] naturalDiscoveryExceptions = new String[0];

    @Config.Comment({
            "Log one INFO line the first time each enchantment is blocked at each source. Diagnostic:",
            "use it to confirm the protection is live (e.g. after opening a librarian's trades) or to",
            "find out which path is offering an enchantment you expected to be gated. Deduplicated, so",
            "it will not flood the log. Read live."
    })
    @Config.Name("Log Natural Discovery Blocks")
    public boolean logNaturalDiscoveryBlocks = false;

    @Config.Comment({
            "OPT-IN, OFF by default. Restricts APPLICATION rather than acquisition: with this on, a",
            "quest-gated enchantment can only be applied from an item stamped with the NBT tag",
            "'insanetweaks_granted:1b', enforced on the anvil. Mint one with:",
            "  /itweaks grantbook <enchantment> [level] [player]   (usable as an FTB Quests command reward)",
            "",
            "It is OFF because the design goal is to limit how these enchantments are OBTAINED while",
            "leaving them applied normally on an anvil - and this flag breaks that: it also refuses a",
            "book taken from JEI/creative, since Forge's AnvilUpdateEvent carries no player reference in",
            "1.12.2 and therefore admits no creative bypass. Acquisition is handled instead by",
            "'Block Natural Discovery' plus the per-mod source mixins, which do not touch the anvil.",
            "",
            "Turn it on only if you want a hard belt-and-braces guarantee and accept that every legit",
            "book must come from the command or carry the tag. Read live."
    })
    @Config.Name("Require Grant Marker")
    public boolean requireGrantMarker = false;

    @Config.Comment({
            "Add a tooltip line to an unmarked quest-gated item explaining that it cannot be applied,",
            "so a player who finds one is told why rather than left with a silently dead anvil.",
            "Only has any effect while 'Require Grant Marker' is on. Read live."
    })
    @Config.Name("Grant Marker Tooltip")
    public boolean grantMarkerTooltip = true;
}
