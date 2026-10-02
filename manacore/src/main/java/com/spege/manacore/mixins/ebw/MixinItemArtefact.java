package com.spege.manacore.mixins.ebw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.manacore.compat.ebw.EbwFuel;

import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.item.ItemStack;

/**
 * Blinds the Demonic Seal's ({@code charm_hunger_casting}) upstream trigger, which compares the
 * held wand's mana with the spell cost - mana that no longer moves. {@link EbwFuel} replaces it.
 *
 * <p>{@code onSpellCastPreEvent} reads {@code IManaStoringItem.getMana} exactly once, in that
 * branch ({@code 729: invokeinterface} in EBW 4.3.19), so the redirect needs no ordinal. If a
 * future EBW adds a second read there, it would be blinded too - re-check with {@code javap}
 * before bumping EBW.
 */
@Mixin(value = ItemArtefact.class, remap = false)
public abstract class MixinItemArtefact {

    @Redirect(method = "onSpellCastPreEvent", at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/item/IManaStoringItem;getMana(Lnet/minecraft/item/ItemStack;)I"),
            remap = false)
    private static int manacore$blindHungerCastingGate(IManaStoringItem item, ItemStack stack) {
        return EbwFuel.upstreamGateMana(item, stack);
    }
}
