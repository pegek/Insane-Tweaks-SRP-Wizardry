package com.spege.insanetweaks.util;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.items.fruit.BaseBaubleFruitItem;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketBaubleFruitProgress;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;

/**
 * How many Bauble Fruits a player has consumed, and whether they may consume another.
 *
 * <p>The authoritative state is the per-type flag each fruit writes into the player's
 * {@code PERSISTED_NBT_TAG}. That NBT is <b>server-only</b> — {@code EntityPlayer.getEntityData()}
 * is never synced — so the client keeps a mirror fed by {@link PacketBaubleFruitProgress}:
 * a bitmask over {@link ModItems#getAllBaubleFruits()} plus the active cap.
 *
 * <p>Keeping both sides in step matters because the refusal happens in
 * {@code onItemRightClick}, which runs on both sides. If the client thought the fruit was
 * edible and the server did not, the eating animation would start and then silently abort.
 */
public final class BaubleFruitProgress {

    /** Client mirror: bit i is set when {@code getAllBaubleFruits()[i]} has been consumed. */
    private static int clientConsumedMask;

    /** Client mirror of the active cap. 0 means "no cap". */
    private static int clientCap;

    /** Cached fruit array — {@link ModItems#getAllBaubleFruits()} hands out a defensive copy. */
    private static Item[] fruits;

    private BaubleFruitProgress() {
    }

    private static Item[] fruits() {
        if (fruits == null) {
            fruits = ModItems.getAllBaubleFruits();
        }
        return fruits;
    }

    // =========================================================================
    // Queries — side-aware, safe to call from common code
    // =========================================================================

    /** True when this player has already consumed this specific fruit type. */
    public static boolean isConsumed(EntityPlayer player, BaseBaubleFruitItem fruit) {
        if (player == null || fruit == null) {
            return false;
        }
        if (player.world != null && player.world.isRemote) {
            int index = indexOf(fruit);
            return index >= 0 && (clientConsumedMask & (1 << index)) != 0;
        }
        return fruit.isConsumedBy(player);
    }

    /** Number of Bauble Fruits this player has consumed. */
    public static int countEaten(EntityPlayer player) {
        if (player == null) {
            return 0;
        }
        if (player.world != null && player.world.isRemote) {
            return Integer.bitCount(clientConsumedMask);
        }
        int count = 0;
        for (Item item : fruits()) {
            if (item instanceof BaseBaubleFruitItem && ((BaseBaubleFruitItem) item).isConsumedBy(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The active cap, or 0 when capping is disabled. On the client this is the value the
     * server sent, so a server-side config change is respected even if the client's own
     * config file disagrees.
     */
    public static int getCap(EntityPlayer player) {
        if (player != null && player.world != null && player.world.isRemote) {
            return clientCap;
        }
        return ModConfig.baubleFruits.enableFruitCap ? ModConfig.baubleFruits.maxFruitsEaten : 0;
    }

    /** True when the player has hit the configured ceiling and may not consume any more fruits. */
    public static boolean isCapReached(EntityPlayer player) {
        int cap = getCap(player);
        return cap > 0 && countEaten(player) >= cap;
    }

    /**
     * Fruits this player could still receive: not yet consumed, and within the cap.
     * Used by the Corrupted Fruit's smart roll so its gamble can never pay out nothing.
     */
    public static java.util.List<BaseBaubleFruitItem> getGrantableFruits(EntityPlayer player) {
        java.util.List<BaseBaubleFruitItem> available = new java.util.ArrayList<BaseBaubleFruitItem>();
        if (isCapReached(player)) {
            return available;
        }
        // Under legacy Baubles every fruit grants the same +1 Luck and there are no slot types
        // to check, so the slot filter applies to the BaublesEX path only.
        boolean checkSlotTypes = ModItems.isBaublesExPresent();
        for (Item item : fruits()) {
            if (item instanceof BaseBaubleFruitItem) {
                BaseBaubleFruitItem fruit = (BaseBaubleFruitItem) item;
                if (isConsumed(player, fruit)) {
                    continue;
                }
                if (checkSlotTypes && fruit.getBaublesExSlotType() == null) {
                    continue;
                }
                available.add(fruit);
            }
        }
        return available;
    }

    // =========================================================================
    // Sync
    // =========================================================================

    /** Recomputes the mask server-side and pushes it to that player's client. */
    public static void sync(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        int mask = 0;
        Item[] all = fruits();
        for (int i = 0; i < all.length; i++) {
            if (all[i] instanceof BaseBaubleFruitItem && ((BaseBaubleFruitItem) all[i]).isConsumedBy(player)) {
                mask |= 1 << i;
            }
        }
        int cap = ModConfig.baubleFruits.enableFruitCap ? ModConfig.baubleFruits.maxFruitsEaten : 0;
        InsaneTweaksNetwork.CHANNEL.sendTo(new PacketBaubleFruitProgress(mask, cap), player);
    }

    /** Called by the packet handler on the client thread. */
    public static void setClientState(int consumedMask, int cap) {
        clientConsumedMask = consumedMask;
        clientCap = cap;
    }

    private static int indexOf(Item fruit) {
        Item[] all = fruits();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == fruit) {
                return i;
            }
        }
        return -1;
    }
}
