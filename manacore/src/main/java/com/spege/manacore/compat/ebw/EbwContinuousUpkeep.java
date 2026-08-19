package com.spege.manacore.compat.ebw;

import javax.annotation.Nullable;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Charges the per-tick upkeep of a continuous (channelled) spell to the player's unified pool.
 *
 * <p>🚨 <b>Why this is not in {@link EbwSpellCostHandler} with everything else.</b>
 * {@code SpellCastEvent.Post} fires exactly ONCE per cast, not once per tick. EBW 4.3.19,
 * {@code ItemWand.cast} bytecode:
 * <pre>
 *   43: ifeq  376        // Spell.cast() returned false - skip everything below
 *   46: iload 5          // castingTick
 *   48: ifne  72         // castingTick != 0 - SKIP the Post event entirely
 *   51..71: EVENT_BUS.post(new SpellCastEvent$Post(...))
 * </pre>
 * {@code cast} itself IS called every tick while channelling (from {@code onUsingTick}), but the
 * event it posts is gated on {@code castingTick == 0}. Upkeep driven off {@code Post} therefore
 * ran once, on the first tick, and a channelled spell was very nearly free. There is no
 * per-tick, post-success EVENT to move it to - so it moves to the per-tick, post-success CALL
 * SITE instead: the {@code consumeMana} invocation that
 * {@link com.spege.manacore.mixins.ebw.MixinItemWand} already redirects.
 *
 * <p>That call site satisfies the founding rule of this mod - never charge for a spell that did
 * not happen - as strictly as {@code Post} does, and for the same reason: the {@code ifeq 376} at
 * offset 43 means nothing below it runs unless {@code Spell.cast()} returned {@code true}.
 *
 * <p>The cadence is EBW's own and is deliberately reproduced rather than smoothed out.
 * {@code ItemWand.getDistributedCost} charges {@code cost/2 + cost%2} every 20th tick and
 * {@code cost/2} every 10th, which sums to exactly {@code cost} per second for any integer cost,
 * odd ones included - so mirroring it loses nothing, and keeps our deduction in step with the
 * figure EBW itself shows for the spell. (Measured across all 189 spell definitions in EBW
 * 4.3.19: after the {@code none} placeholder at 0 and {@code snowball} at 1, the cheapest spell
 * in the game costs 5, and costs go up in steps of 5 - so the integer arithmetic here has no
 * realistic case where it rounds a real spell down to free.)
 */
public final class EbwContinuousUpkeep {

    private EbwContinuousUpkeep() {
    }

    /**
     * Deducts one tick's worth of a channelled spell's cost. A no-op for non-continuous spells,
     * which are charged once in {@link EbwSpellCostHandler#onSpellPost}, and on most ticks even
     * for continuous ones - EBW's distribution charges on every 10th tick and nothing in between.
     *
     * <p>Uses {@link ManaAPI#spendQuiet} rather than {@code spend}: this runs on a per-tick path,
     * and the periodic flush in {@code ManaRegenHandler} delivers the value to the client within
     * half a second, which is the same latency an immediate send would achieve here anyway.
     */
    public static void charge(@Nullable EntityPlayer player, @Nullable Spell spell,
            @Nullable SpellModifiers modifiers, int castingTick) {
        if (player == null || spell == null || modifiers == null || !spell.isContinuous) {
            return;
        }
        double cost = SpellCostResolver.resolveContinuousTick(player, spell, modifiers, castingTick);
        if (cost > 0.0D) {
            ManaAPI.spendQuiet(player, cost);
        }
    }

    /** Whether the EBW bridge is switched on; read live, see {@code EbwCategory.enabled}. */
    public static boolean enabled() {
        return ManaCoreConfig.ebw.enabled;
    }
}
