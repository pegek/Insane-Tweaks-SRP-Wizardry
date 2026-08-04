package com.spege.enchanteraser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Enchant Eraser — makes an enchantment unobtainable <b>without</b> unregistering it.
 *
 * <p>The mod this replaces ({@code disench}) removes the enchantment from the Forge registry while the
 * {@code ench:[{id,lvl}]} NBT stays on player gear. Anything that then has to resolve that NBT — the
 * anvil, the enchantment list — crashes once, and the weapon frequently disappears afterwards. Here the
 * enchantment stays registered, so the NBT resolves and the tooltip renders; it is the <em>sources</em>
 * that are closed instead.
 *
 * <p>🚨 The obvious implementation does not work and was tried first: a mixin on
 * {@code net.minecraft.enchantment.Enchantment} overriding {@code canApplyAtEnchantingTable} /
 * {@code canApply} / {@code isAllowedOnBooks} to return false. Those methods are virtual and mod
 * enchantments override them — verified with {@code javap}, SoManyEnchantments' {@code EnchantmentBase}
 * overrides two of the three and {@code EnchantmentSplitshot} overrides all three — so an injection into
 * the base class never executes. Electroblob's Wizardry gets away with the pattern only because it
 * overrides those methods <b>on its own classes</b>. Every mixin here therefore patches the
 * <b>caller</b>, which intercepts the call instruction no matter which subclass implements the method.
 *
 * <p>Mixin-only (no registry objects). See {@code EraserState} for the whole policy and the mixin
 * javadocs for which vanilla path each one closes.
 */
@Mod(modid = EnchantEraser.MODID,
        name = EnchantEraser.NAME,
        version = EnchantEraser.VERSION,
        acceptableRemoteVersions = "*")
public class EnchantEraser {

    public static final String MODID = "enchanteraser";
    public static final String NAME = "Enchant Eraser";
    public static final String VERSION = "1.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    /** Forge has already populated the {@code @Config} fields by the time preInit runs. */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        EraserState.rebuild(EnchantEraserConfig.disabledEnchantments);
    }

    /**
     * The enchantment registry is complete here, so this is the first point at which "how many of the
     * configured entries actually exist" can be answered. That count is the one piece of feedback
     * {@code disench} never gave — a misspelled registry name silently did nothing.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        EraserState.logSummary(LOGGER);
    }
}
