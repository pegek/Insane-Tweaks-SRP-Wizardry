package com.spege.srpwizcore.mixins.spartanfire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chaosbuffalo.spartanfire.items.ItemDragonBolt;
import com.oblivioussp.spartanweaponry.item.ItemCrossbow;
import com.spege.srpwizcore.dragonranged.DragonWeaponRegistry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Lets the dragonsteel crossbows load Spartan Fire's dragon bolts. Spartan Fire does this for its
 * own dragonbone tier by calling {@code ItemDragonBolt.findDragonBoneBolt} before {@code findAmmo};
 * the dragonsteel tier never gets that call, and {@code findAmmo} cannot see dragon bolts at all
 * because {@code ItemDragonBolt extends Item} rather than {@code ItemBolt}.
 *
 * <p>HEAD injection returning early only when a dragon bolt is actually found, so ordinary bolts
 * stay a working fallback.
 *
 * <p>{@code findAmmo} is Spartan Weaponry's own method on {@code ItemCrossbow}, not an override of
 * anything vanilla, so SRG never renames it and the selector carries the single plain name.
 */
@Mixin(value = ItemCrossbow.class, remap = false)
public abstract class MixinSwCrossbowDragonAmmo {

    @Inject(method = "findAmmo", at = @At("HEAD"), cancellable = true, remap = false)
    private void srpwizcore$preferDragonBolt(EntityPlayer player,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!DragonWeaponRegistry.prefersDragonAmmo((Item) (Object) this)) {
            return;
        }
        ItemStack found = ItemDragonBolt.findDragonBoneBolt(player);
        if (!found.isEmpty()) {
            cir.setReturnValue(found);
        }
    }
}
