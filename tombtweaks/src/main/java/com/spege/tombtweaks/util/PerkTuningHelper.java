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
}
