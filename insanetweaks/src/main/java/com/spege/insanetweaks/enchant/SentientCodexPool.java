package com.spege.insanetweaks.enchant;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.SentientCodexCategory;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Decides which enchantments Sentient Codex is allowed to raise, and how far.
 *
 * <p>This used to be one hardcoded blacklist ({@code fortune}, {@code efficiency}, {@code looting},
 * {@code silk_touch}) plus a single {@code maxLevelsAboveCap} applied to everything. Both were
 * blunt: the list had to be maintained by hand for every mod in the pack, and a flat headroom meant
 * a VERY_RARE enchantment gained exactly as much as a COMMON one.
 *
 * <p>The rules are now mostly derived from the enchantment itself, so an unknown modded enchantment
 * is classified without anyone naming it:
 * <ul>
 * <li><b>Single-level enchantments are skipped.</b> An author who declared {@code getMaxLevel() == 1}
 *     defined no level scaling, so level II is at best inert and at worst breaks - this one rule
 *     covers Silk Touch, Infinity, Flame, Channeling and the great majority of modded toggles.</li>
 * <li><b>Curses are raised, but last.</b> They get {@code capBonusCurse} rather than their rarity's
 *     bonus, which is the smallest headroom on offer: a curse still grows, it just stops growing
 *     first.</li>
 * <li><b>Headroom follows rarity.</b> {@code getMaxLevel() + capBonus<Rarity>}, so the enchantments
 *     that were already the hardest to get gain the least.</li>
 * <li><b>Type matching is optional</b> ({@code requireTypeMatch}, off by default): with it on, an
 *     enchantment another mod pushed onto an item its own {@code EnumEnchantmentType} rejects is
 *     left alone.</li>
 * </ul>
 *
 * <p>The explicit lists remain as overrides. {@code excluded} is also what makes Sentient Codex and
 * Mending mutually exclusive - see {@link #isIncompatible}, which
 * {@link EnchantmentSentientCodex#canApplyTogether} consults so one config list drives both the
 * boost and the anvil refusal.
 *
 * <p>Everything is read live; there is no cache. Each query walks two short config arrays, and the
 * caller already runs once per {@code tickInterval}.
 */
public final class SentientCodexPool {

    private SentientCodexPool() {
    }

    /** Why an enchantment is not boosted, for {@code /itweaks codexpool} to report. */
    public enum Verdict {
        BOOSTED("boosted"),
        SELF("the Codex itself"),
        NOT_WHITELISTED("not on the whitelist"),
        EXCLUDED("on the exclusion list"),
        SINGLE_LEVEL("single-level enchantment"),
        WRONG_TYPE("does not apply to this item"),
        AT_CAP("already at its boosted cap");

        private final String reason;

        Verdict(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return this.reason;
        }
    }

    /** Whether this enchantment may be raised on this stack at all, ignoring its current level. */
    public static boolean canBoost(Enchantment ench, ItemStack stack) {
        return verdict(ench, stack, -1) == Verdict.BOOSTED;
    }

    /**
     * Full classification.
     *
     * @param currentLevel the level on the stack, or a negative number to skip the cap check
     */
    public static Verdict verdict(Enchantment ench, ItemStack stack, int currentLevel) {
        if (ench == null) {
            return Verdict.EXCLUDED;
        }
        if (EnchantmentSentientCodex.INSTANCE != null && ench == EnchantmentSentientCodex.INSTANCE) {
            return Verdict.SELF;
        }

        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        String name = registryName(ench);

        // A non-empty whitelist is absolute: it replaces every derived rule below, which is the
        // point of having one.
        if (cfg.included != null && cfg.included.length > 0) {
            if (!listContains(cfg.included, name)) {
                return Verdict.NOT_WHITELISTED;
            }
        } else {
            if (listContains(cfg.excluded, name)) {
                return Verdict.EXCLUDED;
            }
            if (cfg.skipSingleLevelEnchants && ench.getMaxLevel() <= 1) {
                return Verdict.SINGLE_LEVEL;
            }
            if (cfg.requireTypeMatch && !typeAccepts(ench, stack)) {
                return Verdict.WRONG_TYPE;
            }
        }

        if (currentLevel >= 0 && currentLevel >= capFor(ench)) {
            return Verdict.AT_CAP;
        }
        return Verdict.BOOSTED;
    }

    /**
     * The highest level Sentient Codex will raise this enchantment to: its own maximum plus the
     * headroom its rarity (or being a curse) earns it.
     */
    public static int capFor(Enchantment ench) {
        SentientCodexCategory cfg = ModConfig.enchantments.sentientCodex;
        if (ench == null) {
            return 0;
        }
        int bonus;
        if (ench.isCurse()) {
            bonus = cfg.capBonusCurse;
        } else {
            switch (ench.getRarity()) {
                case COMMON:
                    bonus = cfg.capBonusCommon;
                    break;
                case UNCOMMON:
                    bonus = cfg.capBonusUncommon;
                    break;
                case RARE:
                    bonus = cfg.capBonusRare;
                    break;
                case VERY_RARE:
                default:
                    bonus = cfg.capBonusVeryRare;
                    break;
            }
        }
        return ench.getMaxLevel() + bonus;
    }

    /**
     * Whether Sentient Codex refuses to share an item with this enchantment.
     *
     * <p>Deliberately the same {@code excluded} list the boost uses. Sentient Codex repairs the item
     * it lives on, which is Mending's whole job - letting both sit on one item would make the Codex
     * strictly additive rather than a choice, and keeping the two answers on one config list means
     * they cannot drift apart.
     */
    public static boolean isIncompatible(Enchantment other) {
        if (other == null) {
            return false;
        }
        return listContains(ModConfig.enchantments.sentientCodex.excluded, registryName(other));
    }

    private static boolean typeAccepts(Enchantment ench, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return true; // nothing to test against; do not reject on a missing subject
        }
        // `type` is nullable on modded enchantments that hand-roll canApply instead.
        return ench.type == null || ench.type.canEnchantItem(stack.getItem());
    }

    private static String registryName(Enchantment ench) {
        ResourceLocation name = ench.getRegistryName();
        return name == null ? "" : name.toString();
    }

    private static boolean listContains(String[] list, String name) {
        if (list == null || name.isEmpty()) {
            return false;
        }
        for (String entry : list) {
            if (entry != null && entry.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
