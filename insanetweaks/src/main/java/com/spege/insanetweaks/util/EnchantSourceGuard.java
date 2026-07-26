package com.spege.insanetweaks.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.util.ResourceLocation;

/**
 * Single policy for "which enchantments may NOT be discovered by natural means". Every InsaneTweaks
 * enchantment is quest-gated in the modpack: the player is meant to receive it as a finished
 * enchanted book from a quest reward, never to stumble on it in a dungeon chest, buy it from a
 * librarian or roll it at an enchanting table.
 *
 * <p><b>The rule is by namespace, not by a list.</b> Anything whose registry domain is
 * {@code insanetweaks} is blocked, so a future enchantment inherits the protection the moment it is
 * registered — there is no list to remember to update. {@code enchantments.naturalDiscoveryExceptions}
 * is the escape hatch for deliberately putting one back into circulation.
 *
 * <p>WHAT THIS DOES NOT TOUCH — the intended delivery routes stay fully open:
 * <ul>
 * <li>A pre-built enchanted book handed out by a quest reward, command or creative menu: nothing
 *     random is involved, so nothing here runs.</li>
 * <li>Applying that book to an item on an <b>anvil</b>. {@code ContainerRepair} consults only
 *     {@code Enchantment.canApply} (verified in bytecode — it never calls
 *     {@code canApplyAtEnchantingTable} or {@code isTreasureEnchantment}), and we leave
 *     {@code canApply} permissive.</li>
 * <li>The creative/JEI listing of the enchanted book. {@code ItemEnchantedBook.getSubItems} filters
 *     on the enchantment's {@code type} only, so the book stays visible and searchable.</li>
 * <li>A loot table that names the enchantment <b>explicitly</b>
 *     ({@code {"function":"enchant_randomly","enchantments":["insanetweaks:swift_picking"]}}). That is
 *     a deliberate pack-author choice, not natural discovery, and the {@code EnchantRandomly} mixin
 *     only filters the auto-discovery branch.</li>
 * </ul>
 *
 * <p>The blocking itself happens in three mixins under {@code mixins/enchant/} — see
 * {@code com.spege.insanetweaks.mixins.enchant.MixinEnchantmentHelperNaturalDiscovery} for the map of
 * which vanilla path each one closes.
 */
public final class EnchantSourceGuard {

    /**
     * Registry names already reported under {@code logNaturalDiscoveryBlocks}, so turning the flag on
     * yields one line per enchantment per site instead of a flood (the enchanting table rebuilds its
     * candidate list on every inventory change). Concurrent because chest loot is rolled during chunk
     * generation, which NoiseThreader runs off the server thread.
     */
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private EnchantSourceGuard() {
    }

    /**
     * Master switch, read live. Callers use it to keep a zero-allocation fast path when the whole
     * guard is off; {@link #isNaturallyBlocked} checks it again, so skipping this is never unsafe.
     */
    public static boolean isActive() {
        return ModConfig.enchantments.blockNaturalDiscovery;
    }

    /**
     * True when this enchantment must not be handed out by any random/generated source.
     * Reads {@code enchantments.blockNaturalDiscovery} live — the mixins that call this are applied
     * unconditionally, so the flag is an early-return here and not a mixin-application gate.
     */
    public static boolean isNaturallyBlocked(Enchantment enchantment) {
        if (enchantment == null || !ModConfig.enchantments.blockNaturalDiscovery) {
            return false;
        }
        ResourceLocation id = enchantment.getRegistryName();
        if (id == null || !InsaneTweaksMod.MODID.equals(id.getResourceDomain())) {
            return false;
        }
        for (String exception : ModConfig.enchantments.naturalDiscoveryExceptions) {
            if (exception == null) {
                continue;
            }
            String trimmed = exception.trim();
            if (trimmed.equalsIgnoreCase(id.toString()) || trimmed.equalsIgnoreCase(id.getResourcePath())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Remove every blocked entry from a candidate list that vanilla has just built. Mutates in place
     * because the caller returns that very list.
     *
     * @param source short label naming the vanilla path, for the optional log line
     */
    public static void stripBlocked(List<EnchantmentData> candidates, String source) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        Iterator<EnchantmentData> it = candidates.iterator();
        while (it.hasNext()) {
            EnchantmentData data = it.next();
            if (data != null && isNaturallyBlocked(data.enchantment)) {
                it.remove();
                logOnce(data.enchantment, source);
            }
        }
    }

    /** One INFO line per enchantment per source, and only under {@code logNaturalDiscoveryBlocks}. */
    public static void logOnce(Enchantment enchantment, String source) {
        if (!ModConfig.enchantments.logNaturalDiscoveryBlocks || enchantment == null) {
            return;
        }
        ResourceLocation id = enchantment.getRegistryName();
        String key = (id == null ? "?" : id.toString()) + "@" + source;
        if (LOGGED.add(key)) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] natural discovery blocked: "
                    + (id == null ? "?" : id.toString()) + " via " + source
                    + " (quest-gated; see enchantments.naturalDiscoveryExceptions to allow it)");
        }
    }
}
