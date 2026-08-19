package com.spege.manacore.compat.wizardryutils;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;

import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * Soft integration with the {@code wizardryutils} mod, which registers its own
 * {@code RangedAttribute}s on the player to modify spell casting (a global {@code COST}
 * attribute plus per-element variants). We want to respect that attribute instead of
 * building a second, competing cost system in the same pack - but {@code wizardryutils}
 * is optional, is not present in {@code libs/}, and must never become a compile-time
 * dependency of this mod. So the binding here is entirely reflective: {@link Loader#isModLoaded}
 * gates it, and the attribute field is looked up by name via {@link Class#forName} /
 * {@link Class#getDeclaredField}. When the mod is absent, or anything about that lookup
 * fails or changes shape upstream, this bridge degrades to a no-op rather than crashing
 * class-load - which is also why the failure path catches both {@code Exception} and
 * {@code LinkageError} (see {@link #init()}) plus a logged warning, not a hard requirement.
 *
 * <p>The neutral / "no influence" return value is {@code 1.0}, not {@code 0.0}. This bridge
 * hands back a <em>multiplier</em> that {@link com.spege.manacore.core.CostMath#resolveCost}
 * multiplies against the base cost - {@code 0.0} would mean "every spell is free", which is
 * the opposite of "this mod has nothing to say about the cost". Every return path below
 * (mod absent, attribute not bound, instance missing on this player) must keep returning
 * {@code 1.0} for that reason; do not "simplify" any of them to {@code 0.0}.
 */
public final class WizardryUtilsBridge {

    private static final String MODID = "wizardryutils";
    private static final String ATTRIBUTES_CLASS = "com.windanesz.wizardryutils.server.Attributes";
    private static final String COST_FIELD = "COST";

    /**
     * Sane bounds for the value read off the foreign attribute. {@code IAttributeInstance
     * #getAttributeValue()} is already clamped by {@code wizardryutils}'s own {@code
     * RangedAttribute} construction in the ordinary case - but that construction is outside
     * our control and outside our visibility (no source, no jar in {@code libs/}), so we do
     * not get to assume it stayed sane across a {@code wizardryutils} update. {@link
     * com.spege.manacore.core.CostMath#resolveCost} only rejects non-finite and negative
     * *products*; a merely huge-but-finite multiplier sails straight through it and then
     * gets truncated by {@code (int)} narrowing further down the chain (see {@link
     * com.spege.manacore.core.CostMath#continuousSecondCost}), landing on {@code
     * Integer.MAX_VALUE} instead of erroring - a silent, confusing failure mode rather than
     * a caught one. Clamping here, at the one point where we hold a bare unmultiplied
     * attribute value, converts that into a bounded and still-meaningful number. The bounds
     * mirror our own {@code ebw.costMultiplier} config range ({@code EbwCategory}, {@code
     * @Config.RangeDouble(min = 0.0D, max = 100.0D)}) precisely because both numbers feed the
     * exact same multiplication chain in {@link com.spege.manacore.core.CostMath#resolveCost}
     * and there is no principled reason for a foreign mod's multiplier to be allowed a wider
     * range than our own.
     */
    private static final double MIN_COST_MULTIPLIER = 0.0D;
    private static final double MAX_COST_MULTIPLIER = 100.0D;

    private static boolean available;
    @Nullable
    private static IAttribute costAttribute;

    /**
     * Guards the one-time "clamped a wizardryutils multiplier" warning below. Deliberately not
     * {@code volatile} and the write is not synchronized: this is a logging-frequency flag, not
     * correctness state, so the worst case of a torn/racy write is one duplicate warning line on
     * a hot path - acceptable, and cheaper than adding synchronization to a per-tick call for a
     * cosmetic guarantee.
     */
    private static boolean clampWarned;

    private WizardryUtilsBridge() {
    }

    /**
     * Resolves and caches the {@code COST} attribute. Must be called exactly once, from
     * {@code ManaCoreMod.init(FMLInitializationEvent)} before {@code proxy.init(event)} runs and
     * before any spell can be cast - never lazily from {@link #getCostMultiplier}. This class
     * used to initialize itself lazily on first use, guarded by a plain (non-volatile) boolean
     * set at the top of the method; that had a real race (one thread could observe the flag
     * already set while {@code available}/{@code costAttribute} were still mid-write, and would
     * then read the pre-init defaults permanently, with no retry and no visibility guarantee
     * between threads) and no verified guarantee that spell casting is single-threaded to begin
     * with. Explicit, single-call initialization at a known point in mod startup removes the
     * class of bug instead of patching it.
     *
     * <p>Catches both {@code Exception} and {@link LinkageError}. The single-argument {@link
     * Class#forName(String)} used below runs the target class's static initializer as part of
     * resolution - and we do not control, and cannot see the source of, whatever {@code
     * wizardryutils}'s {@code <clinit>} does in a version we have not tested against. If that
     * initializer throws, or references a class that is not present in some mod combination, the
     * JVM raises {@link ExceptionInInitializerError} or {@link NoClassDefFoundError} - both
     * {@code Error}, not {@code Exception}, so a plain {@code catch (Exception e)} would not stop
     * either one from propagating into the caller (i.e. into an actual spell-cast attempt).
     * {@code LinkageError} is the common supertype of both, and is caught deliberately narrowly:
     * not {@code Throwable}, so an {@code OutOfMemoryError} or {@code StackOverflowError} still
     * propagates rather than being silently swallowed by a soft-integration shim.
     */
    public static void init() {
        if (!Loader.isModLoaded(MODID)) {
            ManaCoreMod.LOGGER.info("[ManaCore] wizardryutils absent - spell cost attributes not used");
            return;
        }

        try {
            Class<?> clazz = Class.forName(ATTRIBUTES_CLASS);
            Field field = clazz.getDeclaredField(COST_FIELD);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof IAttribute) {
                costAttribute = (IAttribute) value;
                available = true;
                ManaCoreMod.LOGGER.info("[ManaCore] wizardryutils COST attribute bound");
            } else {
                ManaCoreMod.LOGGER.warn("[ManaCore] wizardryutils COST is not an IAttribute - skipping");
            }
        } catch (Exception e) {
            ManaCoreMod.LOGGER.warn("[ManaCore] failed to bind wizardryutils COST attribute", e);
        } catch (LinkageError e) {
            ManaCoreMod.LOGGER.warn("[ManaCore] failed to load wizardryutils COST attribute class", e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * Returns the player's current {@code wizardryutils} spell cost multiplier, clamped to
     * {@code [MIN_COST_MULTIPLIER, MAX_COST_MULTIPLIER]}, or {@code 1.0} - "no influence" -
     * when the mod is absent, the attribute could not be bound, or this player carries no
     * instance of it. Assumes {@link #init()} has already run; stateless otherwise, so it is
     * safe to call on a hot path (e.g. once per tick for a continuous spell).
     */
    public static double getCostMultiplier(@Nullable EntityPlayer player) {
        if (!available || player == null || costAttribute == null) {
            return 1.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(costAttribute);
        if (instance == null) {
            return 1.0D;
        }
        double percent = instance.getAttributeValue();
        if (Double.isNaN(percent)) {
            return 1.0D;
        }
        // wizardryutils declares its attributes as RangedAttribute(null, name, 100.0, -500.0, MAX),
        // i.e. the value is a PERCENTAGE whose neutral point is 100, not a multiplier whose neutral
        // point is 1. Reading it as a multiplier makes every spell cost a hundred times its real
        // price - which reads as "not enough mana" on a full pool for anything but the cheapest
        // spells, and is exactly the bug this conversion fixes. Verified against wizardryutils
        // 1.3.1 by decompiling com.windanesz.wizardryutils.server.Attributes; re-check the default
        // if that mod is ever updated, because nothing here would notice the neutral point moving.
        double value = percent / 100.0D;
        if (value < MIN_COST_MULTIPLIER) {
            warnClampedOnce(value, MIN_COST_MULTIPLIER);
            return MIN_COST_MULTIPLIER;
        }
        if (value > MAX_COST_MULTIPLIER) {
            warnClampedOnce(value, MAX_COST_MULTIPLIER);
            return MAX_COST_MULTIPLIER;
        }
        return value;
    }

    /**
     * Logs a warning the first time a {@code wizardryutils} multiplier is actually clamped, then
     * stays silent on every later clamp. This method can run once per tick for a continuous
     * spell, so an unconditional log here would be a real per-tick cost on the server thread;
     * one-time is enough to tell a pack maintainer that {@code wizardryutils} is handing out an
     * out-of-range value, without turning that into ongoing log spam.
     */
    private static void warnClampedOnce(double value, double clampedTo) {
        if (clampWarned) {
            return;
        }
        clampWarned = true;
        ManaCoreMod.LOGGER.warn("[ManaCore] wizardryutils COST multiplier {} out of range - clamped to {}",
                value, clampedTo);
    }
}
