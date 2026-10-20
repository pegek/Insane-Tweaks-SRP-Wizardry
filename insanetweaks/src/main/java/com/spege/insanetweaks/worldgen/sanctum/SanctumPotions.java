package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * Fills a sanctum shulker box with 2-4 Sanctum Elixirs (SanctumElixirs), drinkable or splash, by
 * position. An effect whose mod is missing is skipped; an elixir left with nothing is not placed.
 */
public final class SanctumPotions {

    private static final int COLOUR = 0x6A1B9A;

    private SanctumPotions() {
    }

    public static void fill(IInventory box, long seed, int x, int y, int z) {
        int size = box.getSizeInventory();
        if (size == 0) {
            return;
        }
        int count = 2 + (int) (SanctumNoise.at(seed, x, y, z, 92) * 3);
        for (int i = 0; i < count; i++) {
            double[] noise = new double[SanctumElixirs.NOISE_VALUES];
            for (int k = 0; k < noise.length; k++) {
                noise[k] = SanctumNoise.at(seed, x, y, z, 200 + i * SanctumElixirs.NOISE_VALUES + k);
            }
            boolean splash = SanctumNoise.at(seed, x, y, z, 97 + i) < 0.5D;
            ItemStack elixir = elixir(noise, splash);
            if (elixir.isEmpty()) {
                continue;
            }
            int slot = Math.min(size - 1, (int) (SanctumNoise.at(seed, x, y, z, 101 + i) * size));
            while (!box.getStackInSlot(slot).isEmpty()) {
                slot = (slot + 1) % size;
            }
            box.setInventorySlotContents(slot, elixir);
        }
    }

    private static ItemStack elixir(double[] noise, boolean splash) {
        NBTTagList effects = new NBTTagList();
        for (String[] e : SanctumElixirs.recipe(noise)) {
            Potion potion = Potion.getPotionFromResourceLocation(e[0]);
            if (potion != null) {
                PotionEffect effect = new PotionEffect(potion, Integer.parseInt(e[2]), Integer.parseInt(e[1]));
                effects.appendTag(effect.writeCustomPotionEffectToNBT(new NBTTagCompound()));
            }
        }
        if (effects.tagCount() == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(splash ? Items.SPLASH_POTION : Items.POTIONITEM);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("CustomPotionEffects", effects);
        tag.setInteger("CustomPotionColor", COLOUR);
        stack.setTagCompound(tag);
        stack.setTranslatableName("item.insanetweaks.sanctum_elixir.name");
        return stack;
    }
}
