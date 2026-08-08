package com.spege.tombtweaks.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * Decides whether grave decay is allowed to eat a particular stack.
 *
 * <p>Three independent config lists, any one of which is enough to protect a stack: a root-NBT
 * marker, an enchantment registry name, or an item registry name. What protection then <i>means</i>
 * is {@code graveDecayProtectedNeverDecay} — either "never" or "last", decided by the caller in
 * {@code GraveDecayHandler}, not here.
 *
 * <p>🚨 Deliberately generic. Nothing in this class knows about any particular mod: the NBT list is
 * pure string matching against whatever the pack puts in the config, and a marker no installed mod
 * writes simply never matches. The shipped default happens to name Insane Tweaks' Ashen Legacy
 * property, in the same way the effect pools and raid lists already name {@code ancientspellcraft:},
 * {@code srparasites:} and friends — a soft reference that costs nothing when absent, not a
 * dependency.
 *
 * <p>Parsed once and cached; {@code TombTweaksConfig}'s change handler calls {@link #invalidate()},
 * so edits apply without a restart.
 */
public final class GraveDecayProtection {

    private GraveDecayProtection() {
    }

    /**
     * The parsed config, as one immutable bundle.
     *
     * <p>🚨 One field, not four. The config GUI calls {@link #invalidate()} from its own thread
     * while a world tick may be halfway through a decay check; with a field per list, a query could
     * read one list, have all of them nulled, and NPE on the next. Swapping a single reference means
     * a query either sees the whole old snapshot or the whole new one.
     */
    private static volatile Snapshot snapshot;

    private static final class Snapshot {
        /**
         * Root NBT markers, as tag name to the set of values that count. An empty value set means
         * "presence of the tag is enough".
         */
        final Map<String, Set<String>> nbtMarkers;
        /** Enchantment registry names, lower-cased. */
        final Set<String> enchantNames;
        /** Item registry names, lower-cased. */
        final Set<String> itemNames;
        /** Item registry name prefixes, lower-cased. */
        final List<String> itemPrefixes;

        Snapshot(Map<String, Set<String>> nbtMarkers, Set<String> enchantNames,
                Set<String> itemNames, List<String> itemPrefixes) {
            this.nbtMarkers = nbtMarkers;
            this.enchantNames = enchantNames;
            this.itemNames = itemNames;
            this.itemPrefixes = itemPrefixes;
        }
    }

    /** Drops the parsed lists so the next query re-reads the config. */
    public static void invalidate() {
        snapshot = null;
    }

    /** True when at least one of the lists claims this stack. */
    public static boolean isProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Snapshot snap = current();
        return matchesItem(stack, snap) || matchesNbt(stack, snap) || matchesEnchantment(stack, snap);
    }

    private static Snapshot current() {
        Snapshot snap = snapshot;
        if (snap == null) {
            snap = new Snapshot(
                    parseMarkers(TombTweaksConfig.tombstone.graveDecayProtectedNbtStrings),
                    parseNames(TombTweaksConfig.tombstone.graveDecayProtectedEnchantments),
                    parseNames(TombTweaksConfig.tombstone.graveDecayProtectedItems),
                    parsePrefixes(TombTweaksConfig.tombstone.graveDecayProtectedItemPrefixes));
            // A benign race: two threads may both build one. They are equivalent, so whichever wins
            // is fine and neither can be observed half-built.
            snapshot = snap;
        }
        return snap;
    }

    private static List<String> parsePrefixes(String[] raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static Set<String> parseNames(String[] raw) {
        Set<String> out = new HashSet<String>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static Map<String, Set<String>> parseMarkers(String[] raw) {
        Map<String, Set<String>> out = new HashMap<String, Set<String>>();
        if (raw == null) {
            return out;
        }
        // Tags named by at least one bare entry. "Presence is enough" is the wider rule, so it wins
        // over any 'tag=value' entry for the same tag no matter what order they were listed in -
        // hence a second pass rather than deciding as we go.
        Set<String> presenceOnly = new HashSet<String>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Split on the FIRST '=' only: an NBT value is free-form and may well contain another.
            int split = trimmed.indexOf('=');
            String tag = (split < 0 ? trimmed : trimmed.substring(0, split)).trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (split < 0) {
                presenceOnly.add(tag);
                out.put(tag, new HashSet<String>());
                continue;
            }
            if (presenceOnly.contains(tag)) {
                continue;
            }
            Set<String> values = out.get(tag);
            if (values == null) {
                values = new HashSet<String>();
                out.put(tag, values);
            }
            values.add(trimmed.substring(split + 1).trim());
        }
        for (String tag : presenceOnly) {
            out.put(tag, new HashSet<String>());
        }
        return out;
    }

    private static boolean matchesItem(ItemStack stack, Snapshot snap) {
        if (stack.getItem() == null) {
            return false;
        }
        if (snap.itemNames.isEmpty() && snap.itemPrefixes.isEmpty()) {
            return false;
        }
        ResourceLocation registryName = stack.getItem().getRegistryName();
        if (registryName == null) {
            return false;
        }
        String name = registryName.toString().toLowerCase();
        if (snap.itemNames.contains(name)) {
            return true;
        }
        for (String prefix : snap.itemPrefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesNbt(ItemStack stack, Snapshot snap) {
        if (snap.nbtMarkers.isEmpty() || !stack.hasTagCompound()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        for (Map.Entry<String, Set<String>> marker : snap.nbtMarkers.entrySet()) {
            if (!tag.hasKey(marker.getKey())) {
                continue;
            }
            Set<String> wanted = marker.getValue();
            if (wanted.isEmpty()) {
                return true; // presence is enough
            }
            for (String value : readStrings(tag, marker.getKey())) {
                if (wanted.contains(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The string values a root tag offers: either the tag itself (type 8) or every string in it
     * (type 9 holding type 8). Anything else contributes nothing.
     */
    private static List<String> readStrings(NBTTagCompound tag, String key) {
        List<String> out = new ArrayList<String>();
        if (tag.hasKey(key, 8)) {
            out.add(tag.getString(key));
        } else if (tag.hasKey(key, 9)) {
            NBTTagList list = tag.getTagList(key, 8);
            for (int i = 0; i < list.tagCount(); i++) {
                out.add(list.getStringTagAt(i));
            }
        }
        return out;
    }

    private static boolean matchesEnchantment(ItemStack stack, Snapshot snap) {
        if (snap.enchantNames.isEmpty() || !stack.hasTagCompound()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        // Both lists, not one-or-the-other: an enchanted book carries StoredEnchantments and could
        // in principle carry ench too.
        return matchesEnchantList(tag, "ench", snap)
                || matchesEnchantList(tag, "StoredEnchantments", snap);
    }

    private static boolean matchesEnchantList(NBTTagCompound tag, String key, Snapshot snap) {
        if (!tag.hasKey(key)) {
            return false;
        }
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            // getInteger, not getShort: JustEnoughIDs widens enchantment ids past 32767 and rewrites
            // vanilla's own reads, but not ours. getInteger reads a short tag fine, so this is safe
            // without JEID too.
            Enchantment ench = Enchantment.getEnchantmentByID(list.getCompoundTagAt(i).getInteger("id"));
            if (ench == null) {
                continue;
            }
            ResourceLocation name = ench.getRegistryName();
            if (name != null && snap.enchantNames.contains(name.toString().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
