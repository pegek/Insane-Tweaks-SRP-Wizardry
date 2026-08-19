package com.spege.manacore.core;

/**
 * Pure spell cost math. ZERO Minecraft types.
 */
public final class CostMath {

    private CostMath() {
    }

    /**
     * Replicates ItemWand.getDistributedCost(int, int) from EBW 4.3.19.
     * We do not call the original because it is `protected static` - and the duplicate
     * still needs its own test so that a change on the EBW side becomes detectable.
     */
    public static int distributedCost(int cost, int castingTick) {
        if (castingTick % 20 == 0) {
            return cost / 2 + cost % 2;
        }
        if (castingTick % 10 == 0) {
            return cost / 2;
        }
        return 0;
    }

    /**
     * @param baseCost          Spell.getCost()
     * @param spellMultiplier   SpellModifiers.get(SpellModifiers.COST)
     * @param configMultiplier  our multiplier from manacore.cfg
     * @param foreignMultiplier multiplier from wizardryutils (1.0 when that mod is absent)
     */
    public static double resolveCost(int baseCost, double spellMultiplier,
            double configMultiplier, double foreignMultiplier) {
        double cost = baseCost * spellMultiplier * configMultiplier * foreignMultiplier;
        return cost < 0.0D ? 0.0D : cost;
    }

    /**
     * Fraction of mana refunded after a successful cast, computed from the wand's
     * capacity SURPLUS above the baseline capacity. Always in [0, 1].
     *
     * @param wandCapacity     getManaCapacity(stack), i.e. tier + `storage` upgrades
     * @param baselineCapacity capacity below which there is no refund at all
     * @param capacityStep     surplus points per which we accrue `fractionPerStep`
     */
    public static double refundFraction(int wandCapacity, int baselineCapacity,
            int capacityStep, double fractionPerStep) {
        if (capacityStep <= 0 || fractionPerStep <= 0.0D) {
            return 0.0D;
        }
        int surplus = wandCapacity - baselineCapacity;
        if (surplus <= 0) {
            return 0.0D;
        }
        double fraction = ((double) surplus / capacityStep) * fractionPerStep;
        return fraction > 1.0D ? 1.0D : fraction;
    }

    /** Our mana -> foreign mod's units. Scale <= 0 means 1:1. */
    public static float toForeignUnits(double ours, double scale) {
        return scale <= 0.0D ? (float) ours : (float) (ours * scale);
    }

    /** Foreign mod's units -> our mana. Scale <= 0 means 1:1. */
    public static double fromForeignUnits(float foreign, double scale) {
        return scale <= 0.0D ? foreign : foreign / scale;
    }
}
