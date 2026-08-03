package com.spege.tombtweaks.util;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory;

/**
 * The values behind the native Tombstone perks, read from config.
 *
 * <p>Every method takes the value Tombstone hardcoded and returns either it or the configured
 * replacement, so a mixin handler is a single call with nothing to get wrong, and the master switch
 * is honoured in one place. Same split as {@code CurseReliefHelper}: the arithmetic lives here, the
 * mixin only redirects.
 *
 * <p>Lives outside {@code com.spege.tombtweaks.mixins} — that package may only hold mixins.
 */
public final class PerkTuningHelper {

    private PerkTuningHelper() {}

    private static TombstoneCategory.PerkTuningConfig cfg() {
        return TombTweaksConfig.tombstone.perkTuning;
    }

    private static boolean active() {
        return TombTweaksConfig.tombstone.enableTombstoneTweaks;
    }

    /** Book of Disenchantment: enchantments pulled before the perk adds any. Native 2. */
    public static int scribeBaseEnchants(int nativeValue) {
        return active() ? cfg().scribeEnchantsAtLevelZero : nativeValue;
    }

    /**
     * Book of Disenchantment: turns the raw Scribe level into the number of extra enchantments.
     *
     * <p>Scaling the level rather than adding a second constant is what keeps this to one anchor —
     * Tombstone writes the bonus as a bare {@code + level}, with no multiplier to modify.
     */
    public static int scribeScaledLevel(int level) {
        return active() ? level * cfg().scribeEnchantsPerLevel : level;
    }

    /** Grave key re-enchant: percentage points per Jailer level. Native 20. */
    public static int jailerKeyChancePerLevel(int nativeValue) {
        return active() ? cfg().jailerKeyChancePerLevel : nativeValue;
    }

    /** Extra mob-drop roll: percentage points per Treasure Seeker level. Native 20. */
    public static int treasureSeekerLootChancePerLevel(int nativeValue) {
        return active() ? cfg().treasureSeekerBonusLootChancePerLevel : nativeValue;
    }

    /** Experience given back: percentage points per Memento Mori level. Native 20. */
    public static int mementoMoriXpKeptPerLevel(int nativeValue) {
        return active() ? cfg().mementoMoriXpKeptPerLevel : nativeValue;
    }

    /** Gladiator: damage share added to hits dealt and removed from hits taken. Native 0.05. */
    public static float gladiatorDamagePerLevel(float nativeValue) {
        return active() ? (float) cfg().gladiatorDamageModifierPerLevel : nativeValue;
    }

    /** Shadow Walker: visibility share removed per level. Native 0.1. */
    public static double shadowWalkerVisibilityPerLevel(double nativeValue) {
        return active() ? cfg().shadowWalkerVisibilityReductionPerLevel : nativeValue;
    }

    /** Rune Inscriber: a tablet survives with chance {@code level / divisor}. Native divisor 10. */
    public static int runeInscriberKeepDivisor(int nativeValue) {
        return active() ? cfg().runeInscriberKeepChanceDivisor : nativeValue;
    }

    // ------------------------------------------------------------------
    // The same numbers again, in the units the Knowledge of Death tooltip
    // prints them in. Tombstone builds the tooltip from its own literals
    // rather than from the effect code, so a perk whose effect we retune
    // would otherwise keep advertising the stock figure.
    // ------------------------------------------------------------------

    /** Gladiator's bonus line is a percentage, where the effect is a fraction. Native 5. */
    public static int gladiatorDamagePercentPerLevel(int nativeValue) {
        return active() ? (int) Math.round(cfg().gladiatorDamageModifierPerLevel * 100.0D) : nativeValue;
    }

    /** Shadow Walker's bonus line is a percentage, where the effect is a fraction. Native 10. */
    public static int shadowWalkerVisibilityPercentPerLevel(int nativeValue) {
        return active() ? (int) Math.round(cfg().shadowWalkerVisibilityReductionPerLevel * 100.0D) : nativeValue;
    }

    /**
     * Rune Inscriber's bonus line is a percentage per level, where the effect is a divisor —
     * {@code level / divisor} is {@code level * (100 / divisor)} percent. Native 10.
     *
     * <p>Integer division, so a divisor that does not divide 100 rounds the printed figure down
     * (divisor 3 prints 33% for a real 33.3%). The effect itself is exact either way.
     */
    public static int runeInscriberKeepPercentPerLevel(int nativeValue) {
        if (!active()) return nativeValue;
        return 100 / Math.max(1, cfg().runeInscriberKeepChanceDivisor);
    }
}
