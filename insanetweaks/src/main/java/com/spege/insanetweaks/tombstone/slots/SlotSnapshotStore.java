package com.spege.insanetweaks.tombstone.slots;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Persistence for {@link SlotSnapshot}, keyed to the grave it belongs to.
 *
 * <p>Stored under {@link EntityPlayer#PERSISTED_NBT_TAG}, because that is the only part of a
 * player's entity data Forge carries across the respawn — and a snapshot taken at death is not
 * read until the player walks back to their grave, which is necessarily after respawning.
 *
 * <p>A snapshot is written <i>pending</i>: at the moment of death the grave does not exist yet, so
 * there is nothing to key it to. Tombstone stamps the grave with {@code System.currentTimeMillis()}
 * a few instructions later in {@code DeathHandler}, and {@code MixinTombstonePlayerGrave} hands
 * that exact value here. Two graves in a row therefore stay distinguishable no matter which order
 * they are recovered in.
 */
public final class SlotSnapshotStore {

    private static final String KEY = "insanetweaks_slot_snapshots";

    /** Deaths remembered per player. Beyond this the oldest is dropped, grave or no grave. */
    private static final int MAX_SNAPSHOTS = 5;

    /**
     * How far a grave's death date may sit from a pending snapshot's capture time for the fallback
     * match to accept it. Both are {@code System.currentTimeMillis()} taken within the same tick,
     * so the real gap is milliseconds; five seconds is slack, not tolerance.
     */
    private static final long FALLBACK_WINDOW_MS = 5000L;

    private SlotSnapshotStore() {
    }

    public static void store(EntityPlayer player, SlotSnapshot snapshot) {
        List<SlotSnapshot> all = read(player);
        all.add(snapshot);
        while (all.size() > MAX_SNAPSHOTS) {
            all.remove(0);
        }
        write(player, all);
    }

    /**
     * Binds the newest still-unbound snapshot to a grave. Called from the grave's own
     * {@code setOwner}, so "newest pending" is the death that grave was just built for.
     */
    public static void bindPending(EntityPlayer player, long graveDeathTime) {
        List<SlotSnapshot> all = read(player);
        for (int i = all.size() - 1; i >= 0; i--) {
            SlotSnapshot candidate = all.get(i);
            if (candidate.isPending()) {
                candidate.setGraveDeathTime(graveDeathTime);
                write(player, all);
                return;
            }
        }
    }

    /**
     * Removes and returns the snapshot for a grave, or null.
     *
     * <p>The exact match on the grave's death date is the normal path. The fallback exists because
     * the binding mixin is not load-bearing: {@code required:false} means a Tombstone update that
     * moves {@code setOwner} would silently leave every snapshot pending, and a feature that
     * quietly stops working is worse than one that degrades. A pending snapshot captured within a
     * few milliseconds of the grave's death date is that same death.
     */
    public static SlotSnapshot take(EntityPlayer player, long graveDeathTime) {
        List<SlotSnapshot> all = read(player);

        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getGraveDeathTime() == graveDeathTime) {
                SlotSnapshot found = all.remove(i);
                write(player, all);
                return found;
            }
        }

        int bestIndex = -1;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            SlotSnapshot candidate = all.get(i);
            if (!candidate.isPending()) {
                continue;
            }
            long delta = Math.abs(graveDeathTime - candidate.getCapturedAt());
            if (delta <= FALLBACK_WINDOW_MS && delta < bestDelta) {
                bestDelta = delta;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return null;
        }
        SlotSnapshot found = all.remove(bestIndex);
        write(player, all);
        return found;
    }

    private static List<SlotSnapshot> read(EntityPlayer player) {
        List<SlotSnapshot> all = new ArrayList<SlotSnapshot>();
        NBTTagCompound persisted = persisted(player, false);
        if (persisted == null || !persisted.hasKey(KEY, 9)) {
            return all;
        }
        NBTTagList list = persisted.getTagList(KEY, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            all.add(SlotSnapshot.deserialize(list.getCompoundTagAt(i)));
        }
        return all;
    }

    private static void write(EntityPlayer player, List<SlotSnapshot> all) {
        NBTTagCompound persisted = persisted(player, true);
        if (persisted == null) {
            return;
        }
        if (all.isEmpty()) {
            persisted.removeTag(KEY);
            return;
        }
        NBTTagList list = new NBTTagList();
        for (SlotSnapshot snapshot : all) {
            list.appendTag(snapshot.serialize());
        }
        persisted.setTag(KEY, list);
    }

    private static NBTTagCompound persisted(EntityPlayer player, boolean create) {
        NBTTagCompound data = player.getEntityData();
        if (data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        }
        if (!create) {
            return null;
        }
        NBTTagCompound fresh = new NBTTagCompound();
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, fresh);
        return fresh;
    }
}
