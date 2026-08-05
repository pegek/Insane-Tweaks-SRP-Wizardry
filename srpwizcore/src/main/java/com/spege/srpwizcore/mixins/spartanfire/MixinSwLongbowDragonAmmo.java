package com.spege.srpwizcore.mixins.spartanfire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.item.ItemDragonBow;
import com.oblivioussp.spartanweaponry.item.ItemLongbow;
import com.spege.srpwizcore.dragonranged.DragonWeaponRegistry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Lets the dragonsteel longbows load Ice and Fire's dragon arrows. Spartan Fire does this for its
 * own dragonbone tier by calling {@code ItemDragonBow.findDragonBoneArrow} before {@code findAmmo};
 * the dragonsteel tier never gets that call, and {@code findAmmo} cannot see dragon arrows at all
 * because {@code ItemDragonArrow extends ItemGeneric} rather than {@code ItemArrow}.
 *
 * <p>HEAD injection returning early only when a dragon arrow is actually found, so ordinary arrows
 * stay a working fallback.
 *
 * <p>{@code ItemLongbow} overrides {@code ItemBow.findAmmo}, so the selector carries both the dev
 * (MCP) and the runtime (SRG) name.
 */
@Mixin(value = ItemLongbow.class, remap = false)
public abstract class MixinSwLongbowDragonAmmo {

    @Inject(method = { "findAmmo", "func_185060_a" }, at = @At("HEAD"), cancellable = true,
            remap = false)
    private void srpwizcore$preferDragonArrow(EntityPlayer player,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!DragonWeaponRegistry.prefersDragonAmmo((Item) (Object) this)) {
            return;
        }
        ItemStack found = ItemDragonBow.findDragonBoneArrow(player);
        if (!found.isEmpty()) {
            cir.setReturnValue(found);
        }
    }
}
