package com.spege.enchanteraser.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;

import com.spege.enchanteraser.config.EnchantEraserConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * The single source of truth for "which enchantments have been erased", and every operation the mixins
 * and the tooltip handler need to act on that.
 *
 * <p>Lives in {@code util} rather than {@code mixins} because a mixin may not reference a non-mixin
 * class from inside its own package, and because a mixin must never hold static state — a static field
 * initialiser merged into the target's {@code <clinit>} fails verification at load time.
 *
 * <p>The policy is an explicit list, not a namespace rule: this is a general-purpose tool aimed at
 * de-duplicating an enchantment pool across a whole modpack, so the pack author names each entry.
 * Entries that do not resolve to a registered enchantment are ignored silently — the list is expected
 * to keep leftovers from mods that have since been removed — but {@link #logSummary} reports the count,
 * which is the feedback {@code disench} never gave when a registry name was misspelled.
 */
public final class EraserState {

    /**
     * Parsed form of {@code Disabled Enchantments}. Volatile because the config can be edited in game
     * and rebuilt on the client thread while loot generation reads it off a worldgen worker.
     */
    private static volatile Set<ResourceLocation> DISABLED = Collections.emptySet();

    /** How many non-blank lines the config held, so {@link #logSummary} can report "N of M". */
    private static volatile int configuredCount;

    /**
     * Registry names already reported under {@code Log Blocks}, so turning the flag on yields one line
     * per enchantment per source instead of a flood (the enchanting table rebuilds its candidate list
     * on every inventory change). Concurrent because chest loot is rolled during chunk generation,
     * which NoiseThreader runs off the server thread.
     */
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private EraserState() {
    }

    /** Reparse the config list. Safe to call at any time; publishes the new set atomically. */
    public static void rebuild(String[] entries) {
        Set<ResourceLocation> parsed = new HashSet<ResourceLocation>();
        int configured = 0;
        if (entries != null) {
            for (String entry : entries) {
                if (entry == null) {
                    continue;
                }
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                configured++;
                try {
                    parsed.add(new ResourceLocation(trimmed));
                } catch (RuntimeException malformed) {
                    // A ResourceLocation rejects uppercase and stray characters. Same treatment as an
                    // entry naming a removed mod: ignore it, and let the summary line show the shortfall.
                }
            }
        }
        DISABLED = parsed.isEmpty() ? Collections.<ResourceLocation>emptySet() : parsed;
        configuredCount = configured;
        LOGGED.clear();
    }

    /**
     * Fast path for every call site: an empty list must mean the mod is behaviourally absent.
     * Checking this first keeps the disabled case allocation-free.
     */
    public static boolean isEmpty() {
        return DISABLED.isEmpty();
    }

    /** True when this enchantment is on the erase list. Null-safe. */
    public static boolean isDisabled(Enchantment enchantment) {
        if (enchantment == null || DISABLED.isEmpty()) {
            return false;
        }
        ResourceLocation id = enchantment.getRegistryName();
        return id != null && DISABLED.contains(id);
    }

    /**
     * Remove every erased entry from a candidate list vanilla has just built. Mutates in place because
     * the caller returns that very list.
     *
     * @param source short label naming the vanilla path, for the optional log line
     */
    public static void strip(List<EnchantmentData> candidates, String source) {
        if (candidates == null || candidates.isEmpty() || DISABLED.isEmpty()) {
            return;
        }
        Iterator<EnchantmentData> it = candidates.iterator();
        while (it.hasNext()) {
            EnchantmentData data = it.next();
            if (data != null && isDisabled(data.enchantment)) {
                it.remove();
                logOnce(data.enchantment, source);
            }
        }
    }

    /** The first erased enchantment carried by this stack, or null when it is clean. */
    public static Enchantment firstDisabledOn(ItemStack stack) {
        if (stack == null || stack.isEmpty() || DISABLED.isEmpty()) {
            return null;
        }
        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (isDisabled(enchantment)) {
                return enchantment;
            }
        }
        return null;
    }

    /**
     * Every erased enchantment on this stack with its level, in the order the NBT stores them — which is
     * the order the tooltip lists them. Empty map when the stack is clean, so callers can skip cheaply.
     *
     * <p>Goes through {@code EnchantmentHelper.getEnchantments}, which special-cases
     * {@code ENCHANTED_BOOK} and reads {@code StoredEnchantments} rather than {@code ench}.
     */
    public static Map<Enchantment, Integer> disabledOn(ItemStack stack) {
        if (stack == null || stack.isEmpty() || DISABLED.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Enchantment, Integer> found = null;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (isDisabled(entry.getKey())) {
                if (found == null) {
                    found = new LinkedHashMap<Enchantment, Integer>();
                }
                found.put(entry.getKey(), entry.getValue());
            }
        }
        return found == null ? Collections.<Enchantment, Integer>emptyMap() : found;
    }

    /**
     * Delete every erased enchantment from a stack's NBT.
     *
     * <p>🚨 This edits the {@link NBTTagList} directly and must keep doing so.
     * {@code EnchantmentHelper.setEnchantments} cannot be used to <em>remove</em> anything from a book:
     * verified in the Forge sources, for {@code Items.ENCHANTED_BOOK} it calls
     * {@code ItemEnchantedBook.addEnchantment} per entry and never clears {@code StoredEnchantments}
     * first, so a get → remove → set round trip would duplicate the surviving entries and leave the
     * removed one in place.
     *
     * <p>Both {@code ItemEnchantedBook.getEnchantments} and {@code ItemStack.getEnchantmentTagList}
     * hand back the live list held in the stack's tag compound, so mutating it sticks. When the stack
     * has no tag compound at all they return a fresh empty list instead, which this method leaves alone.
     *
     * @param source short label naming the path, for the optional log line
     * @return true when the stack now carries no enchantments at all
     */
    public static boolean stripFromStack(ItemStack stack, String source) {
        if (stack == null || stack.isEmpty() || DISABLED.isEmpty()) {
            return false;
        }
        boolean book = stack.getItem() == Items.ENCHANTED_BOOK;
        NBTTagList list = book ? ItemEnchantedBook.getEnchantments(stack) : stack.getEnchantmentTagList();
        if (list.hasNoTags()) {
            return false;
        }
        boolean changed = false;
        // Backwards: removeTag shifts every later index down.
        for (int i = list.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            // 🚨 getInteger, NOT getShort, even though vanilla writes a short here. JustEnoughIDs
            // widens enchantment ids: its ItemEnchantedBook mixin writes "id" with setInteger and its
            // EnchantmentHelper mixin reads it with getInteger. Those redirects patch *vanilla* call
            // sites, not ours, so a getShort here would silently truncate any id above 32767 and
            // resolve to the wrong enchantment or none. getInteger is correct either way: both
            // NBTTagShort and NBTTagInt are NBTPrimitive, and getInteger widens rather than truncates.
            Enchantment enchantment = Enchantment.getEnchantmentByID(tag.getInteger("id"));
            if (isDisabled(enchantment)) {
                list.removeTag(i);
                logOnce(enchantment, source);
                changed = true;
            }
        }
        if (changed && list.hasNoTags()) {
            if (stack.hasTagCompound()) {
                stack.getTagCompound().removeTag(book ? "StoredEnchantments" : "ench");
            }
            return true;
        }
        return false;
    }

    /**
     * A uniformly picked enchantment that is not erased, for rollers that hand us a finished result and
     * expect a non-null replacement. Returns null only if literally everything in the registry is
     * erased — callers must treat that as "keep what you had" rather than passing null on.
     */
    public static Enchantment pickReplacement(Random random) {
        if (random == null) {
            return null;
        }
        List<Enchantment> allowed = new ArrayList<Enchantment>();
        for (Enchantment enchantment : Enchantment.REGISTRY) {
            if (!isDisabled(enchantment)) {
                allowed.add(enchantment);
            }
        }
        return allowed.isEmpty() ? null : allowed.get(random.nextInt(allowed.size()));
    }

    /** One INFO line per enchantment per source, and only under {@code Log Blocks}. */
    public static void logOnce(Enchantment enchantment, String source) {
        if (!EnchantEraserConfig.logBlocks || enchantment == null) {
            return;
        }
        ResourceLocation id = enchantment.getRegistryName();
        String key = (id == null ? "?" : id.toString()) + "@" + source;
        if (LOGGED.add(key)) {
            com.spege.enchanteraser.EnchantEraser.LOGGER.info("[EnchantEraser] blocked "
                    + (id == null ? "?" : id.toString()) + " at " + source);
        }
    }

    /**
     * One startup line: how many configured entries actually resolve to a registered enchantment.
     * A shortfall is not an error — the list keeps leftovers from removed mods on purpose — but it is
     * the only way to notice a typo, which under {@code disench} failed completely silently.
     */
    public static void logSummary(Logger logger) {
        int matched = 0;
        for (ResourceLocation id : DISABLED) {
            if (Enchantment.REGISTRY.getObject(id) != null) {
                matched++;
            }
        }
        logger.info("[EnchantEraser] applied {} / {} entries", Integer.valueOf(matched),
                Integer.valueOf(configuredCount));
        if (matched < configuredCount) {
            logger.info("[EnchantEraser] {} entry/entries did not match a registered enchantment - "
                    + "expected for leftovers from removed mods, otherwise check the registry names",
                    Integer.valueOf(configuredCount - matched));
        }
    }
}
