package com.spege.manacore.compat.ebw;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Electroblob's Wizardry spell casting into the player's unified mana pool.
 *
 * <p>🚨 {@code Pre} and {@code Tick} are gates ONLY - they must never subtract mana. EBW posts
 * {@code Post} only after {@code Spell.cast()} returns {@code true}, so a spell that never
 * actually fires (interrupted, out of range, vetoed by another mod's {@code Pre} listener) costs
 * nothing. All spending happens in {@code Post} (and, for continuous spells, the refund and
 * progression happen once in {@code Finish}). This is the central difference from the
 * {@code player_mana} mod this project started from, which spent in {@code Pre}.
 *
 * <p>For a continuous (channelled) spell, {@code ItemWand.onUsingTick} calls {@code cast()} every
 * tick once the charge-up period is over, so {@code Post} fires every tick too. Two consequences
 * follow directly from that:
 * <ul>
 *   <li>Progression must NOT be granted in {@code Post} for continuous spells - it would be
 *   granted once per tick, and ten seconds of channelling at the default
 *   {@code progressionPerCast = 0.05} would add 10 permanent points against a default cap of 50.
 *   Progression is granted once, in {@code Finish}, for both spell kinds.</li>
 *   <li>Continuous upkeep must use {@link ManaAPI#spendQuiet}, never {@link ManaAPI#spend} -
 *   the latter syncs immediately, which would mean one packet per player per tick for the whole
 *   duration of the channel.</li>
 * </ul>
 *
 * <p>🚨 This handler only READS {@link electroblob.wizardry.util.SpellModifiers}, exactly like
 * {@link SpellCostResolver} - see that class's javadoc for why writing our multiplier back into
 * the modifiers would make EBW's own wand-capacity gate reject casts that the player's actual
 * mana pool could easily afford.
 */
public class EbwSpellCostHandler {

    @SubscribeEvent
    public void onSpellPre(SpellCastEvent.Pre event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        double cost = SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers());

        if (ManaAPI.getMana(player) < cost) {
            event.setCanceled(true);
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("manacore.message.not_enough"), true);
            }
        }
    }

    @SubscribeEvent
    public void onSpellTick(SpellCastEvent.Tick event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        double cost = SpellCostResolver.resolveContinuousTick(
                player, event.getSpell(), event.getModifiers(), event.getCount());

        if (cost > 0.0D && ManaAPI.getMana(player) < cost) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onSpellPost(SpellCastEvent.Post event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote) {
            return;
        }

        if (event.getSpell().isContinuous) {
            // Upkeep only. Refund and progression are deferred to Finish (see class javadoc) so
            // that they are granted once per cast, not once per tick of channelling.
            double tickCost = SpellCostResolver.resolveContinuousTick(
                    player, event.getSpell(), event.getModifiers(), player.getItemInUseMaxCount());
            if (tickCost > 0.0D) {
                ManaAPI.spendQuiet(player, tickCost);
            }
            return;
        }

        double cost = SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers());
        if (cost > 0.0D) {
            ManaAPI.spend(player, cost);
        }
        applyRefund(player, cost);
        ManaAPI.addProgression(player, ManaCoreConfig.pool.progressionPerCast);
    }

    @SubscribeEvent
    public void onSpellFinish(SpellCastEvent.Finish event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote || !event.getSpell().isContinuous) {
            return;
        }

        // The refund basis must match what was actually deducted tick by tick in Post, not the
        // raw resolved cost: Post spends CostMath.continuousSecondCost(resolve(...)) per second
        // (ceiling-rounded), never the fractional resolve(...) value itself. Rebuilding the same
        // per-second figure here keeps the refund consistent with the real spend.
        double perSecondCost = CostMath.continuousSecondCost(
                SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers()));
        double totalCost = perSecondCost * (event.getCount() / 20.0D);

        applyRefund(player, totalCost);
        ManaAPI.addProgression(player, ManaCoreConfig.pool.progressionPerCast);
    }

    /**
     * Refunds a fraction of {@code cost} based on the casting wand's capacity surplus (the
     * {@code storage} upgrade). Checks both hands - EBW allows casting from the off hand - and
     * prefers the main hand when both happen to hold a wand.
     */
    private void applyRefund(EntityPlayer player, double cost) {
        if (cost <= 0.0D) {
            return;
        }
        ItemStack wandStack = wandStackOf(player);
        if (wandStack.isEmpty()) {
            return;
        }

        ItemWand wand = (ItemWand) wandStack.getItem();
        double fraction = CostMath.refundFraction(
                wand.getManaCapacity(wandStack),
                ManaCoreConfig.ebw.refundBaselineCapacity,
                ManaCoreConfig.ebw.refundCapacityStep,
                ManaCoreConfig.ebw.refundFractionPerStep);
        if (fraction <= 0.0D) {
            return;
        }

        ManaAPI.add(player, cost * fraction);
    }

    /** Returns the wand stack casting the spell: main hand first, then off hand, else empty. */
    private ItemStack wandStackOf(EntityPlayer player) {
        ItemStack main = player.getHeldItemMainhand();
        if (!main.isEmpty() && main.getItem() instanceof ItemWand) {
            return main;
        }
        ItemStack off = player.getHeldItemOffhand();
        if (!off.isEmpty() && off.getItem() instanceof ItemWand) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private boolean applies(SpellCastEvent event) {
        if (!ManaCoreConfig.ebw.enabled) {
            return false;
        }
        if (event.getSource() != SpellCastEvent.Source.WAND) {
            return false;
        }
        EntityLivingBase caster = event.getCaster();
        return caster instanceof EntityPlayer;
    }
}
