package com.spege.insanetweaks.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Optional compat for Enigmatic Legacy (legacy 1.12.2 port).
 * Detects the rings that gate the Bauble Fruit acquisition loop
 * (fragment drops + sapling growth): the Blessed Ring and — full parity per
 * spec 2026-07-10 — the plain Cursed Ring (Ring of the Seven Curses).
 */
public final class EnigmaticLegacyCompat {

    private static Item blessedRing;
    private static Item cursedRing;
    private static boolean lookedUp = false;

    private EnigmaticLegacyCompat() {
    }

    /**
     * True only when Enigmatic Legacy is installed AND the master interaction switch is on.
     * Gating the ring detection here means the whole Bauble Fruit acquisition path
     * (fragment drops + sapling growth) respects the config switch in one place.
     */
    public static boolean isLoaded() {
        return com.spege.insanetweaks.config.ModConfig.interactions.enableEnigmaticLegacyInteractions
                && Loader.isModLoaded("enigmaticlegacy");
    }

    /**
     * True when the player currently WEARS a ring that unlocks the Corrupted Seed loop:
     * the Blessed Ring OR the plain Cursed Ring — in any baubles slot.
     * False when Enigmatic Legacy or Baubles is absent.
     */
    @SuppressWarnings("null")
    public static boolean isWearingQualifyingRing(EntityPlayer player) {
        if (player == null || !isLoaded() || !Loader.isModLoaded("baubles")) {
            return false;
        }
        if (!lookedUp) {
            blessedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "blessed_ring"));
            cursedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "cursed_ring"));
            lookedUp = true;
        }
        if (blessedRing == null && cursedRing == null) {
            return false;
        }
        baubles.api.cap.IBaublesItemHandler handler = baubles.api.BaublesApi
                .getBaublesHandler((net.minecraft.entity.EntityLivingBase) player);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            Item worn = handler.getStackInSlot(i).getItem();
            if ((blessedRing != null && worn == blessedRing)
                    || (cursedRing != null && worn == cursedRing)) {
                return true;
            }
        }
        return false;
    }
}
