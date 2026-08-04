package com.spege.tombtweaks.mixins.enigmatic;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.Multimap;
import com.spege.tombtweaks.util.CurseReliefHelper;

import keletu.enigmaticlegacy.EnigmaticConfigs;
import keletu.enigmaticlegacy.item.ItemBaseBauble;
import keletu.enigmaticlegacy.item.ItemCursedRing;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Softens the third Ring of the Seven Curses penalty — the armour debuff — for holders of the
 * "Relief for the Damned" perk.
 *
 * <p><b>Why this is not a redirect on the constant any more.</b> Until Enigmatic Legacy 2.6.0 the
 * ring built its own modifiers in {@code ItemCursedRing.createAttributeMap(EntityPlayer)} and
 * {@code onWornTick} re-applied them every server tick, so the relief was a one-line redirect on
 * the {@code armorDebuff} read with the player arriving as the target method's own argument.
 * 2.7.0 moved both halves: the modifiers are now built by {@code fillModifiers} on the shared
 * {@link ItemBaseBauble} base and applied from {@code onEquippedOrLoadedIntoWorld}, and
 * {@code createAttributeMap()} lost its parameter. The constant is still read twice in exactly
 * the same place, but there is no longer a player anywhere on that path — hence the move one
 * frame outwards, to the only point that sees the modifiers and the wearer at the same time.
 *
 * <p><b>Why redirect rather than post-process.</b> The redirect runs <i>before</i> Enigmatic
 * Legacy's own {@code areAttributeModifiersApplied} check, so the softened value is the one that
 * gets applied in the first place. Correcting the attribute afterwards would leave a tick of
 * full-strength armour loss on every equip and would mean re-deriving EL's guards by hand.
 *
 * <p><b>The stale-amount problem this also solves.</b> 2.7.0 only applies the modifiers when
 * {@code areAttributeModifiersApplied} says they are missing, and that check goes through
 * {@code IAttributeInstance.hasModifier}, which in 1.12.2 compares {@link AttributeModifier} by
 * UUID alone. Enigmatic Legacy's two UUIDs are constants, so once a modifier is on the player a
 * changed <i>amount</i> would never replace it — buying or losing a perk level would do nothing
 * until the ring was taken off and put back on. {@link #tombtweaks$dropStaleModifiers} closes
 * that by removing any applied modifier whose amount no longer matches the one we just computed,
 * which makes EL's own check fail and re-apply on the same tick. It runs whether or not relief is
 * active on purpose: losing the perk has to restore the full penalty just as promptly.
 *
 * <p><b>Blast radius.</b> {@link ItemBaseBauble} is the base of every Enigmatic Legacy bauble, so
 * this redirect sits on all of their equip paths. Everything after the {@code instanceof
 * ItemCursedRing} test is therefore dead weight for every other bauble, and that test is the
 * first thing past the original call.
 *
 * <p>Modifiers are matched by amount rather than by UUID: the two the ring builds are the only
 * ones worth exactly {@code -armorDebuff}, and reading the same field EL just read keeps the
 * match correct if that value is reconfigured. Verified with {@code javap -p -c} against
 * enigmaticlegacy-legacy-2.7.0. Re-check on any EL update.
 */
@Mixin(targets = "keletu.enigmaticlegacy.item.ItemBaseBauble", remap = false)
public abstract class MixinItemBaseBaubleArmorRelief {

    @Redirect(
            method = "onEquippedOrLoadedIntoWorld",
            at = @At(value = "INVOKE",
                    target = "Lkeletu/enigmaticlegacy/item/ItemBaseBauble;fillModifiers"
                            + "(Lcom/google/common/collect/Multimap;Lnet/minecraft/item/ItemStack;)V"),
            remap = false)
    private static void tombtweaks$softenCursedRingArmor(ItemBaseBauble bauble,
            Multimap<String, AttributeModifier> modifiers, ItemStack modifierStack,
            ItemStack stack, EntityLivingBase wearer) {
        bauble.fillModifiers(modifiers, modifierStack);
        if (!(bauble instanceof ItemCursedRing) || !(wearer instanceof EntityPlayer)) {
            return;
        }
        float base = EnigmaticConfigs.armorDebuff;
        float softened = CurseReliefHelper.softenDebuff(base, (EntityPlayer) wearer);
        if (softened != base) {
            tombtweaks$rescaleDebuffs(modifiers, -base, -softened);
        }
        tombtweaks$dropStaleModifiers(wearer, modifiers);
    }

    /**
     * Replaces every modifier worth exactly {@code from} with an otherwise identical one worth
     * {@code to}. {@link AttributeModifier} is immutable, so each has to be rebuilt; UUID, name
     * and operation are carried over untouched so Enigmatic Legacy's own bookkeeping still
     * recognises them as its own.
     */
    private static void tombtweaks$rescaleDebuffs(
            Multimap<String, AttributeModifier> modifiers, double from, double to) {
        Multimap<String, AttributeModifier> rescaled = com.google.common.collect.HashMultimap.create();
        boolean changed = false;
        for (Map.Entry<String, AttributeModifier> entry : modifiers.entries()) {
            AttributeModifier modifier = entry.getValue();
            if (modifier.getAmount() == from) {
                rescaled.put(entry.getKey(), new AttributeModifier(
                        modifier.getID(), modifier.getName(), to, modifier.getOperation()));
                changed = true;
            } else {
                rescaled.put(entry.getKey(), modifier);
            }
        }
        if (changed) {
            modifiers.clear();
            modifiers.putAll(rescaled);
        }
    }

    /**
     * Removes any already-applied modifier whose amount differs from the one we want, so that
     * Enigmatic Legacy's UUID-only "is it applied?" test fails and it re-applies the new value.
     */
    private static void tombtweaks$dropStaleModifiers(EntityLivingBase wearer,
            Multimap<String, AttributeModifier> wanted) {
        AbstractAttributeMap attributes = wearer.getAttributeMap();
        for (Map.Entry<String, AttributeModifier> entry : wanted.entries()) {
            IAttributeInstance instance = attributes.getAttributeInstanceByName(entry.getKey());
            if (instance == null) {
                continue;
            }
            AttributeModifier applied = instance.getModifier(entry.getValue().getID());
            if (applied != null && applied.getAmount() != entry.getValue().getAmount()) {
                instance.removeModifier(applied);
            }
        }
    }
}
