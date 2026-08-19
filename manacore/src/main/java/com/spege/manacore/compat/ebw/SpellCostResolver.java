package com.spege.manacore.compat.ebw;

import javax.annotation.Nullable;

import com.spege.manacore.compat.wizardryutils.WizardryUtilsBridge;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The single place in this codebase where the number "how much does this spell cost" is
 * produced. Every caller that needs a spell's mana cost - the event handler that debits the
 * player's pool, any tooltip that displays a cost, any future balance check - must go through
 * here rather than re-deriving the multiplication chain itself, so there is exactly one
 * formula to keep in sync with EBW, our own config and {@code wizardryutils}.
 *
 * <p>🚨 This resolver only READS {@link SpellModifiers#COST}, and must never WRITE to it. The
 * EBW mixin from the wand-neutralization step (see {@code MixinItemWand}) lets EBW's own
 * capacity gate through unmodified - it returns the wand's {@code getManaCapacity(stack)} as
 * the ceiling for a single cast, so wand capacity is the cap a spell's cost is checked
 * against. {@code SpellCastEvent.Pre} fires *before* EBW computes that cost inside {@code
 * canCast}. If this resolver wrote our multiplier back into {@code modifiers}, EBW would then
 * run its own capacity check against an already-ManaCore-scaled cost - and {@code
 * ebw.costMultiplier} goes up to 100 in config, so a scaled cost could exceed even a
 * masterwork wand's capacity (2500) while the player's own mana pool still had plenty left,
 * producing a cast refusal that has nothing to do with the player's actual mana. Keeping this
 * resolver read-only avoids that entirely: our multiplier only ever affects what we deduct
 * from the ManaCore pool, never what EBW checks against the wand.
 */
public final class SpellCostResolver {

    private SpellCostResolver() {
    }

    /**
     * Resolves the full cost of one cast of {@code spell}, combining the spell's own base
     * cost, EBW's own {@code SpellModifiers.COST} (read-only, see class javadoc), our config
     * multiplier, and - when enabled and available - the player's {@code wizardryutils}
     * {@code COST} attribute multiplier (neutral {@code 1.0} otherwise). All rejection of
     * non-finite / negative results happens inside {@link CostMath#resolveCost}.
     */
    public static double resolve(@Nullable EntityPlayer player, Spell spell, SpellModifiers modifiers) {
        double foreign = ManaCoreConfig.ebw.useWizardryUtilsAttributes
                ? WizardryUtilsBridge.getCostMultiplier(player)
                : 1.0D;

        return CostMath.resolveCost(
                spell.getCost(),
                modifiers.get(SpellModifiers.COST),
                ManaCoreConfig.ebw.costMultiplier,
                foreign);
    }

    /**
     * Cost of a single tick of a continuous (channelled) spell, distributed the same way EBW
     * itself distributes it.
     *
     * <p>🚨 Goes through {@link CostMath#continuousSecondCost}, deliberately NOT {@code (int)
     * Math.round(full)} - see {@link CostMath#continuousSecondCost} for why naive rounding can
     * silently turn an infinite or fractional cost into a free or negative one.
     *
     * <p>Returns {@code double} only for consistency with {@link
     * com.spege.manacore.api.ManaAPI#spend(EntityPlayer, double)} - the actual result is always
     * a whole number, never a fractional per-tick cost.
     */
    public static double resolveContinuousTick(@Nullable EntityPlayer player, Spell spell,
            SpellModifiers modifiers, int castingTick) {
        double full = resolve(player, spell, modifiers);
        return CostMath.distributedCost(CostMath.continuousSecondCost(full), castingTick);
    }
}
