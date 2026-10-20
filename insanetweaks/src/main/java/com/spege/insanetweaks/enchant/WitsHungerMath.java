package com.spege.insanetweaks.enchant;

/**
 * The numbers behind Wit's Hunger, the Sentient Codex starvation curse.
 *
 * <p>Wit's Hunger level N weakens like vanilla Weakness N plus Mining Fatigue N, and it must never
 * stack with the real effects: <b>whichever is stronger counts</b>. So the curse never applies its
 * full strength blindly - it applies only the <i>difference</i> between its own level and whatever
 * the vanilla effect from another source already does, and nothing when that is already stronger.
 * The total therefore always equals the stronger of the two.
 *
 * <p>Every vanilla constant is copied from 1.12.2: Weakness is {@code -4} attack damage per level
 * (operation 0), Mining Fatigue is {@code -0.1} attack speed per level (operation 2, i.e. a
 * multiplier) and a dig-speed factor hardcoded in {@code EntityPlayer.getDigSpeed}.
 *
 * <p>Levels are amplifiers (0 = level I); {@link #NONE} means "the vanilla effect is not active".
 * No Minecraft types, so this runs under plain JUnit.
 */
public final class WitsHungerMath {

    private WitsHungerMath() {
    }

    /** Amplifier meaning "no such effect". */
    public static final int NONE = -1;

    /** Vanilla Weakness: attack damage per level, applied as a flat addition. */
    private static final double WEAKNESS_PER_LEVEL = -4.0;
    /** Vanilla Mining Fatigue: attack speed per level, applied as a multiplier. */
    private static final double FATIGUE_SPEED_PER_LEVEL = -0.1;

    /**
     * Flat attack-damage modifier (operation 0) the curse adds, given the amplifier of any real
     * Weakness the entity also has.
     */
    public static double attackDamageModifier(int curseAmp, int weaknessAmp) {
        return WEAKNESS_PER_LEVEL * Math.max(0, curseAmp - weaknessAmp);
    }

    /**
     * Attack-speed modifier (operation 2: the attribute is multiplied by {@code 1 + result}) the
     * curse adds, given the amplifier of any real Mining Fatigue. The factor is clamped at 0, so
     * very high levels mean "no swing left" rather than a negative speed, and an effect already at
     * that floor leaves nothing to add.
     */
    public static double attackSpeedModifier(int curseAmp, int fatigueAmp) {
        if (curseAmp <= fatigueAmp) {
            return 0.0;
        }
        double theirs = speedFactor(fatigueAmp);
        if (theirs <= 0.0) {
            return 0.0;
        }
        return speedFactor(curseAmp) / theirs - 1.0;
    }

    /**
     * Multiplier for the player's dig speed on top of what vanilla already applied for any real
     * Mining Fatigue.
     */
    public static float digSpeedMultiplier(int curseAmp, int fatigueAmp) {
        if (curseAmp <= fatigueAmp) {
            return 1.0f;
        }
        return digFactor(curseAmp) / digFactor(fatigueAmp);
    }

    private static double speedFactor(int amp) {
        return amp < 0 ? 1.0 : Math.max(0.0, 1.0 + FATIGUE_SPEED_PER_LEVEL * (amp + 1));
    }

    /** {@code EntityPlayer.getDigSpeed}'s Mining Fatigue switch, verbatim. */
    private static float digFactor(int amp) {
        switch (amp) {
            case NONE:
                return 1.0f;
            case 0:
                return 0.3f;
            case 1:
                return 0.09f;
            case 2:
                return 0.0027f;
            default:
                return 8.1E-4f;
        }
    }
}
