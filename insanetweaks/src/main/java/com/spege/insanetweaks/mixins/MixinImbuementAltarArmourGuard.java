package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Vetoes an Imbuement Altar armour result before EBW can cast an unregistered item to
 * {@code IManaStoringItem}.
 *
 * <p>{@code getImbuementResult}'s armour branch looks up {@code ItemWizardArmour.getArmour(element,
 * armourClass, armorType)} and builds an {@code ItemStack} straight from whatever it returns, with no
 * check for a miss. For an element with no armour registered under that name - Abomination today, any
 * future element with no armour set tomorrow - the lookup misses, the stack is {@link ItemStack#EMPTY},
 * its item is {@code Items.AIR}, and the very next line casts {@code result.getItem()} to
 * {@code IManaStoringItem}: a {@code ClassCastException} one line before the {@code isEmpty} check that
 * looks like it would have caught this. This is not JEI-only - the altar's tile entity calls this
 * method itself whenever its contents change.
 *
 * <p>Guarded on "the lookup produced no usable armour", not on the element's identity, so this stops
 * firing by itself the day a matching armour set is registered under the {@code ebwizardry} namespace -
 * no edit needed here or in JEI.
 */
@Mixin(value = TileEntityImbuementAltar.class, remap = false)
public abstract class MixinImbuementAltarArmourGuard {

    @Inject(method = "getImbuementResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$vetoUnregisteredArmourImbuement(
            ItemStack input, Element[] receptacleElements, boolean fullLootGen,
            World world, EntityPlayer lastUser, CallbackInfoReturnable<ItemStack> cir) {

        if (!(input.getItem() instanceof ItemWizardArmour)) {
            return;
        }
        ItemWizardArmour armour = (ItemWizardArmour) input.getItem();

        // EBW's own check: only elementless armour is imbuable in the first place.
        if (armour.element != null) {
            return;
        }

        if (receptacleElements.length == 0 || receptacleElements[0] == null) {
            return;
        }
        Element target = receptacleElements[0];
        for (Element el : receptacleElements) {
            if (el != target) {
                return;
            }
        }

        Item result = ItemWizardArmour.getArmour(target, armour.armourClass, armour.armorType);
        if (result instanceof IManaStoringItem) {
            // A real armour piece exists for this element - let EBW build it normally.
            return;
        }

        cir.setReturnValue(ItemStack.EMPTY);
    }
}
