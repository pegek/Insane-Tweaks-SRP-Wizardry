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
     * To re-verify this duplicate after an EBW update: {@code javap -p -c} on
     * {@code electroblob.wizardry.item.ItemWand}, method {@code getDistributedCost}.
     *
     * <p>Never returns a negative value: a negative {@code cost} would fall through the
     * integer division/modulo below as a refund (e.g. {@code distributedCost(-5, 20)} would
     * otherwise be {@code -3}), which nothing downstream is set up to catch. Note this
     * guard is on {@code cost} only - {@code castingTick} is deliberately left unrestricted,
     * matching the original EBW arithmetic (a negative tick that is an exact multiple of
     * 20 or 10 still pays out; see {@code kosztRozlozonySymetriaDlaUjemnychTickow}).
     */
    public static int distributedCost(int cost, int castingTick) {
        if (cost <= 0) {
            return 0;
        }
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
     *
     * <p>This is the last point in the chain that still sees a raw {@code double}: past
     * here the caller rounds to {@code int} (see {@link #continuousSecondCost(double)} and
     * {@link #distributedCost(int, int)}), and that rounding launders a poisoned value into
     * an ordinary-looking number that no later {@code isFinite} check can catch (Infinity
     * rounds to {@code Long.MAX_VALUE}, which truncates to {@code -1} as an {@code int}).
     * {@code foreignMultiplier} in particular is read via reflection off an attribute owned
     * by the {@code wizardryutils} mod, which we do not control - so both NaN and either
     * infinity must be rejected here, not assumed away. {@code cost < 0.0D} alone is not
     * enough: it is {@code false} for both {@code Infinity} ({@code Infinity < 0} is false)
     * and {@code NaN} (every comparison with NaN is false), so both used to slip through.
     */
    public static double resolveCost(int baseCost, double spellMultiplier,
            double configMultiplier, double foreignMultiplier) {
        double cost = baseCost * spellMultiplier * configMultiplier * foreignMultiplier;
        if (!Double.isFinite(cost) || cost < 0.0D) {
            return 0.0D;
        }
        return cost;
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
        // !(fractionPerStep > 0.0D) also catches NaN, unlike `fractionPerStep <= 0.0D`
        // (every comparison with NaN is false, so `<= 0.0D` lets NaN through).
        if (capacityStep <= 0 || !(fractionPerStep > 0.0D)) {
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

    /**
     * Safe rounding for a continuous (channelled) spell's per-second cost, replacing the
     * naive {@code distributedCost((int) Math.round(full), tick)} integration path. Vanilla
     * EBW has the same "round to int before distributing" shape, so a fractional cost being
     * lossy is not a bug we introduced - but our config multiplier lets a single slider land
     * on a fractional total (e.g. {@code full == 0.4}), which vanilla has no way to reach,
     * and rounding-to-nearest there makes the spell permanently free ({@code round(0.4) == 0}).
     *
     * <p>We round up instead: a spell that costs anything per second always costs at least 1,
     * never 0. The tradeoff is deliberate - a 0.4-cost spell now pays 1, i.e. 2.5x, which is
     * a worse per-tick approximation than rounding to nearest. We accept that inaccuracy
     * because the invariant "a spell that costs something is never free" is worth more than
     * precision on fractional costs.
     *
     * @param fullCost the full per-second cost from {@link #resolveCost}; non-positive, NaN
     *                 and infinite values all resolve to 0 (no cost, not "free channel")
     */
    public static int continuousSecondCost(double fullCost) {
        if (!Double.isFinite(fullCost) || fullCost <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(fullCost);
    }
}
