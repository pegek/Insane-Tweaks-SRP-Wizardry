package com.spege.insanetweaks.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Optional compat for Enigmatic Legacy (legacy 1.12.2 port).
 * Currently only detects the Blessed Ring — the acquisition gate for the
 * Bauble Fruit loop (fragment drops + sapling growth).
 */
public final class EnigmaticLegacyCompat {

    private static Item blessedRing;
    private static boolean lookedUp = false;

    private EnigmaticLegacyCompat() {
    }

    /**
     * True only when Enigmatic Legacy is installed AND the master interaction switch is on.
     * Gating the Blessed Ring detection here means the whole Bauble Fruit acquisition path
     * (fragment drops + sapling growth) respects the config switch in one place.
     */
    public static boolean isLoaded() {
        return com.spege.insanetweaks.config.ModConfig.interactions.enableEnigmaticLegacyInteractions
                && Loader.isModLoaded("enigmaticlegacy");
    }

    /**
     * True when the player currently WEARS the Blessed Ring in any baubles slot.
     * False when Enigmatic Legacy or Baubles is absent.
     */
    @SuppressWarnings("null")
    public static boolean isWearingBlessedRing(EntityPlayer player) {
        if (player == null || !isLoaded() || !Loader.isModLoaded("baubles")) {
            return false;
        }
        if (!lookedUp) {
            blessedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "blessed_ring"));
            lookedUp = true;
        }
        if (blessedRing == null) {
            return false;
        }
        baubles.api.cap.IBaublesItemHandler handler = baubles.api.BaublesApi
                .getBaublesHandler((net.minecraft.entity.EntityLivingBase) player);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == blessedRing) {
                return true;
            }
        }
        return false;
    }
}
