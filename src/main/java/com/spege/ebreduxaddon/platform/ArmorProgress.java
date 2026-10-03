package com.spege.ebreduxaddon.platform;

import net.minecraft.world.item.ItemStack;

/** Obrazenia pochloniete przez czesc Grafted, w NBT stacka (double: obrazenia sa ulamkowe). */
public final class ArmorProgress {

    public static final String TAG = "ebreduxaddon:absorbed";

    private ArmorProgress() {
    }

    public static double get(ItemStack stack) {
        return stack.hasTag() ? Math.max(0.0, stack.getTag().getDouble(TAG)) : 0.0;
    }

    public static double add(ItemStack stack, double amount) {
        double next = get(stack) + (amount > 0 && Double.isFinite(amount) ? amount : 0.0);
        stack.getOrCreateTag().putDouble(TAG, next);
        return next;
    }
}
