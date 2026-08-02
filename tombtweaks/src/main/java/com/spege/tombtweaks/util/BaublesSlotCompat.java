package com.spege.tombtweaks.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * The Baubles inventory, reduced to the four operations the grave slot restore needs.
 *
 * <p>A shim rather than direct calls because Baubles is optional: every Baubles type is named
 * inside this class and nowhere else, so a pack without it never reaches a signature that would
 * have to resolve. Same reason {@code EnigmaticLegacyCompat} exists.
 *
 * <p>Writing through {@code setStackInSlot} is the correct way in, not a shortcut around the
 * container: {@code BaublesContainer.onContentsChanged} diffs against its own snapshot and calls
 * {@code onExchange}, so {@code IBauble.onEquipped} fires and the slot is marked for client sync
 * exactly as if the player had placed the item themselves.
 */
public final class BaublesSlotCompat {

    private BaublesSlotCompat() {
    }

    public static boolean isLoaded() {
        return Loader.isModLoaded("baubles");
    }

    /** Number of Baubles slots this player has, or 0 when Baubles is absent. */
    public static int getSlots(EntityPlayer player) {
        if (!isLoaded()) {
            return 0;
        }
        baubles.api.cap.IBaublesItemHandler handler = handler(player);
        return handler == null ? 0 : handler.getSlots();
    }

    public static ItemStack getStack(EntityPlayer player, int slot) {
        if (!isLoaded()) {
            return ItemStack.EMPTY;
        }
        baubles.api.cap.IBaublesItemHandler handler = handler(player);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return handler.getStackInSlot(slot);
    }

    /** True when the slot is free and Baubles agrees the item belongs in it. */
    public static boolean canPlace(EntityPlayer player, int slot, ItemStack stack) {
        if (!isLoaded()) {
            return false;
        }
        baubles.api.cap.IBaublesItemHandler handler = handler(player);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) {
            return false;
        }
        if (!handler.getStackInSlot(slot).isEmpty()) {
            return false;
        }
        return handler.isItemValidForSlot(slot, stack, (EntityLivingBase) player);
    }

    public static void setStack(EntityPlayer player, int slot, ItemStack stack) {
        if (!isLoaded()) {
            return;
        }
        baubles.api.cap.IBaublesItemHandler handler = handler(player);
        if (handler == null || slot < 0 || slot >= handler.getSlots()) {
            return;
        }
        handler.setStackInSlot(slot, stack);
    }

    private static baubles.api.cap.IBaublesItemHandler handler(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        return baubles.api.BaublesApi.getBaublesHandler((EntityLivingBase) player);
    }
}
