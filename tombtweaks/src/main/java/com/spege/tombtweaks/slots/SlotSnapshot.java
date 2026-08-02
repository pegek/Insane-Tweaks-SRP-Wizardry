package com.spege.tombtweaks.slots;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * Where every item a player was carrying sat at the moment they died.
 *
 * <p>Deliberately not a copy of the items — only enough of each one to recognise it again when the
 * grave hands it back: registry name, metadata and a hash of its NBT. A full serialised inventory
 * would be tens of kilobytes rewritten into the player's persisted data on every autosave, and it
 * would also be a second authority on what the player owned. The grave is the authority; this is
 * only a seating plan.
 *
 * <p>The stack size is stored but never matched on. Tombstone's {@code percentLossOnDeath} shrinks
 * stacks inside the grave before anyone else gets a look at them, so a count recorded at death is
 * not a count that survives to recovery.
 */
public final class SlotSnapshot {

    /** Encoded slot space. Player main inventory keeps its own 0-35 indices. */
    public static final int ARMOR_BASE = 100;
    public static final int OFFHAND_SLOT = 150;
    public static final int BAUBLE_BASE = 200;

    /** The grave's death date, which is what binds this snapshot to one grave. 0 while pending. */
    private long graveDeathTime;

    /** Wall clock at capture. Orders the list and feeds the fallback match. */
    private final long capturedAt;

    private final List<Entry> entries;

    public SlotSnapshot(long capturedAt) {
        this.capturedAt = capturedAt;
        this.entries = new ArrayList<Entry>();
    }

    private SlotSnapshot(long graveDeathTime, long capturedAt, List<Entry> entries) {
        this.graveDeathTime = graveDeathTime;
        this.capturedAt = capturedAt;
        this.entries = entries;
    }

    public void add(int encodedSlot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return;
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        entries.add(new Entry(encodedSlot, name.toString(), stack.getItemDamage(),
                tag == null ? 0 : tag.hashCode(), stack.getCount()));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public long getGraveDeathTime() {
        return graveDeathTime;
    }

    public void setGraveDeathTime(long graveDeathTime) {
        this.graveDeathTime = graveDeathTime;
    }

    public boolean isPending() {
        return graveDeathTime == 0L;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    /** True when {@code stack} is the item this entry was taken from. */
    public static boolean matches(Entry entry, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return false;
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null || !entry.item.equals(name.toString())) {
            return false;
        }
        if (entry.meta != stack.getItemDamage()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return entry.nbtHash == (tag == null ? 0 : tag.hashCode());
    }

    public NBTTagCompound serialize() {
        NBTTagCompound root = new NBTTagCompound();
        root.setLong("t", graveDeathTime);
        root.setLong("c", capturedAt);
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("s", entry.slot);
            tag.setString("i", entry.item);
            tag.setInteger("m", entry.meta);
            tag.setInteger("h", entry.nbtHash);
            tag.setInteger("n", entry.count);
            list.appendTag(tag);
        }
        root.setTag("e", list);
        return root;
    }

    public static SlotSnapshot deserialize(NBTTagCompound root) {
        List<Entry> entries = new ArrayList<Entry>();
        NBTTagList list = root.getTagList("e", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            entries.add(new Entry(tag.getInteger("s"), tag.getString("i"), tag.getInteger("m"),
                    tag.getInteger("h"), tag.getInteger("n")));
        }
        return new SlotSnapshot(root.getLong("t"), root.getLong("c"), entries);
    }

    /** One remembered seat. */
    public static final class Entry {
        public final int slot;
        public final String item;
        public final int meta;
        public final int nbtHash;
        public final int count;

        Entry(int slot, String item, int meta, int nbtHash, int count) {
            this.slot = slot;
            this.item = item;
            this.meta = meta;
            this.nbtHash = nbtHash;
            this.count = count;
        }
    }
}
