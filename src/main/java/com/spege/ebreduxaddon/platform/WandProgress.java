package com.spege.ebreduxaddon.platform;

import com.spege.ebreduxaddon.core.Progress;
import net.minecraft.world.item.ItemStack;

/** Licznik symbiozy w NBT stacka rozdzki. */
public final class WandProgress {

    public static final String TAG = "ebreduxaddon:symbiosis";

    private WandProgress() {
    }

    public static long get(ItemStack stack) {
        return stack.hasTag() ? Math.max(0L, stack.getTag().getLong(TAG)) : 0L;
    }

    public static void set(ItemStack stack, long points) {
        stack.getOrCreateTag().putLong(TAG, Math.max(0L, points));
    }

    public static long add(ItemStack stack, double points) {
        long next = Progress.add(get(stack), points);
        set(stack, next);
        return next;
    }
}
