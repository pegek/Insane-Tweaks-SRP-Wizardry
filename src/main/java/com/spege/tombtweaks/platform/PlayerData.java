package com.spege.tombtweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Jedyny pod-tag danych gracza, ktory Forge przenosi przez smierc
 * ({@code ServerPlayer.restoreFrom} kopiuje {@link Player#PERSISTED_NBT_TAG}).
 * Wszystko, co ma przetrwac respawn, musi siedziec tutaj.
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
