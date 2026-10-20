package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;

/** Fills a sanctum shulker box with 2-4 strong or long potions, drinkable or splash, by position. */
public final class SanctumPotions {

    private static final PotionType[] TYPES = { PotionTypes.STRONG_HEALING, PotionTypes.STRONG_REGENERATION,
            PotionTypes.STRONG_STRENGTH, PotionTypes.STRONG_SWIFTNESS, PotionTypes.LONG_FIRE_RESISTANCE,
            PotionTypes.LONG_NIGHT_VISION };

    private SanctumPotions() {
    }

    public static void fill(IInventory box, long seed, int x, int y, int z) {
        int size = box.getSizeInventory();
        if (size == 0) {
            return;
        }
        int count = 2 + (int) (SanctumNoise.at(seed, x, y, z, 92) * 3);
        for (int i = 0; i < count; i++) {
            PotionType type = TYPES[Math.min(TYPES.length - 1, (int) (SanctumNoise.at(seed, x, y, z, 93 + i) * TYPES.length))];
            boolean splash = SanctumNoise.at(seed, x, y, z, 97 + i) < 0.5D;
            ItemStack potion = PotionUtils.addPotionToItemStack(
                    new ItemStack(splash ? Items.SPLASH_POTION : Items.POTIONITEM), type);
            int slot = Math.min(size - 1, (int) (SanctumNoise.at(seed, x, y, z, 101 + i) * size));
            while (!box.getStackInSlot(slot).isEmpty()) {
                slot = (slot + 1) % size;
            }
            box.setInventorySlotContents(slot, potion);
        }
    }
}
