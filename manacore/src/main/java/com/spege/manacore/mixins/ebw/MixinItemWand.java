package com.spege.manacore.mixins.ebw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Neutralizes {@link ItemWand}'s own mana pool as the payer for spell casts, without ever
 * touching the mana value stored in the wand's NBT. Both redirects only change what the wand's
 * {@code canCast}/{@code cast} logic *does* with that stored value - they never write to it - so
 * uninstalling ManaCore leaves every wand working exactly as EBW expects, with no leftover state
 * of ours to clean up.
 *
 * <p>To be precise about the uninstall story: these redirects write nothing, but EBW's own
 * mechanisms still do. The {@code condenser} upgrade, mana flasks and the Arcane Workbench keep
 * recharging the wand while nothing drains it any more, so after a long session a wand will sit
 * at full mana rather than wherever vanilla EBW play would have left it. That is a gift to the
 * player on uninstall, not a corruption, but it is not "byte-identical to never having installed
 * this mod" either.
 *
 * <p>Two consequences of the target being {@code ItemWand} itself, both intentional and neither
 * obvious from this file alone:
 * <ul>
 *   <li>Subclasses are covered too. The evolved wands in the {@code insanetweaks} content mod
 *   extend {@code ItemWand} without overriding {@code canCast}/{@code cast}, so they stop paying
 *   from their own pool as well. That is what we want - they are wands - but it means this mixin
 *   reaches beyond EBW's own items whenever that mod is installed alongside.</li>
 *   <li>The melee damage bonus in {@code ItemWand}'s attack handler gates on the wand not being
 *   mana-empty. Since casting no longer drains the wand and the condenser still refills it, that
 *   bonus becomes effectively permanent instead of rewarding a charged wand. This is a real, if
 *   small, balance shift that falls out of separating two mechanics which used to share one pool.</li>
 * </ul>
 *
 * <p>Two call sites are targeted, both confirmed on EBW 4.3.19 bytecode before writing this
 * class (see the Task 3 report for the full {@code javap} trace):
 * <ul>
 *   <li>{@code ItemWand#canCast} calls {@code getMana(stack)} to compare the wand's current mana
 *   against the spell's cost; redirecting it to {@code getManaCapacity(stack)} makes the gate
 *   compare against the wand's own capacity instead of its current charge, so a spell still fails
 *   the check when it costs more than the wand could *ever* hold. Returning
 *   {@code Integer.MAX_VALUE} instead would have removed that ceiling entirely, letting a wand
 *   with a token capacity cast arbitrarily expensive spells - the capacity is meant to keep
 *   capping a single cast, only the "do I have enough saved up" question is removed.</li>
 *   <li>{@code ItemWand#cast} calls {@code consumeMana(stack, cost, caster)} after a successful
 *   cast to deduct the cost from the wand; skipping that call (instead of calling it) leaves the
 *   NBT mana untouched, i.e. exactly what "not paying with wand mana" means.</li>
 * </ul>
 *
 * <p>Deliberately NOT touched: {@code ItemWand#func_77644_a} (melee weapon-cast on hit) also
 * calls {@code getMana}/{@code consumeMana}, but that is EBW's melee damage path, not spell
 * casting, and is out of scope for this mixin.
 *
 * <p>Both {@link Redirect} handlers take {@link ItemWand} as their first (receiver) parameter,
 * matching the exact owner of the {@code invoke*} instruction being redirected - {@code @Redirect}
 * requires the receiver type to be the precise declaring class of the call site, not a supertype
 * or an implemented interface (see repo memory {@code mixin-redirect-exact-receiver}).
 *
 * <p>🚨 {@link ManaCoreConfig#ebw}'s {@code enabled} flag carries
 * {@code @Config.RequiresMcRestart}, but that annotation does not gate whether this mixin
 * applies - a config value cannot gate mixin application (see {@code EbwCategory}'s own comment
 * on the field). This mixin config has no {@code IMixinConfigPlugin}, so it always applies once
 * EBW is present; the flag is read live, inside each handler, purely as an early-return back to
 * EBW's own behaviour.
 */
@Mixin(value = ItemWand.class, remap = false)
public abstract class MixinItemWand {

    @Redirect(
            method = "canCast",
            at = @At(value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;getMana(Lnet/minecraft/item/ItemStack;)I"),
            remap = false)
    private int manacore$bypassWandManaGate(ItemWand self, ItemStack stack) {
        if (!ManaCoreConfig.ebw.enabled) {
            return self.getMana(stack);
        }
        // Compare against capacity, not current charge: a spell that could never fit in this
        // wand still fails the gate, only the "do you have it saved up right now" check is gone.
        return self.getManaCapacity(stack);
    }

    @Redirect(
            method = "cast",
            at = @At(value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;consumeMana(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/EntityLivingBase;)V"),
            remap = false)
    private void manacore$skipWandManaConsumption(ItemWand self, ItemStack stack, int cost, EntityLivingBase caster) {
        if (!ManaCoreConfig.ebw.enabled) {
            self.consumeMana(stack, cost, caster);
        }
        // else: intentionally do nothing - the wand's own NBT mana is left untouched, and the
        // player's unified mana pool is charged elsewhere (handler added in a later task).
    }
}
