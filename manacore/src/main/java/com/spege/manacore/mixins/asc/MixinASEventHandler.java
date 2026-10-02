package com.spege.manacore.mixins.asc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.manacore.compat.ebw.EbwFuel;

import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.item.ItemStack;

/**
 * Blinds Ancient Spellcraft's mana-artefact trigger (Rings of Mana, Majestic Mana Charm), which
 * fires when the held wand's mana is at or below the spell cost - mana that no longer moves. Left
 * alone, it would drain a ring into a wand nothing spends from. {@link EbwFuel} replaces it.
 *
 * <p>Only the WAND read is redirected: it is the method's single
 * {@code invokeinterface IManaStoringItem.getMana} ({@code 220:} in ASC 1.8.3). The rings' own
 * mana is read through {@code invokevirtual ItemManaArtefact.getMana} - a different owner, which
 * a redirect on the interface does not match. Targeted by name: no ASC jar is on the compile
 * classpath, and every type in the redirected call is EBW's or vanilla's.
 */
@Mixin(targets = "com.windanesz.ancientspellcraft.handler.ASEventHandler", remap = false)
public abstract class MixinASEventHandler {

    @Redirect(method = "onSpellCastPreEvent", at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/item/IManaStoringItem;getMana(Lnet/minecraft/item/ItemStack;)I"),
            remap = false)
    private static int manacore$blindManaArtefactGate(IManaStoringItem item, ItemStack stack) {
        return EbwFuel.upstreamGateMana(item, stack);
    }
}
