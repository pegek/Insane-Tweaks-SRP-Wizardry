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
 * class-load - which is also why the failure path is a caught {@code Exception} plus a
 * logged warning, not a hard requirement.
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

    private static boolean initialized;
    private static boolean available;
    @Nullable
    private static IAttribute costAttribute;

    private WizardryUtilsBridge() {
    }

    /**
     * Lazily resolves and caches the {@code COST} attribute the first time it is needed.
     * Idempotent by design: {@code initialized} is set before any lookup work happens, so a
     * lookup failure never causes a retry on every subsequent call (which would repeat the
     * warning log on a hot path). Not synchronized - this class is only ever driven from the
     * server thread (spell casting is not a multi-threaded path in EBW), so the ordinary
     * single-writer JMM guarantees here are sufficient; a torn read from another thread would
     * at worst see the pre-init defaults ({@code available == false}, i.e. the neutral 1.0
     * path) and try again later, never a crash.
     */
    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

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
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /**
     * Returns the player's current {@code wizardryutils} spell cost multiplier, clamped to
     * {@code [MIN_COST_MULTIPLIER, MAX_COST_MULTIPLIER]}, or {@code 1.0} - "no influence" -
     * when the mod is absent, the attribute could not be bound, or this player carries no
     * instance of it.
     */
    public static double getCostMultiplier(@Nullable EntityPlayer player) {
        init();
        if (!available || player == null || costAttribute == null) {
            return 1.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(costAttribute);
        if (instance == null) {
            return 1.0D;
        }
        double value = instance.getAttributeValue();
        if (Double.isNaN(value)) {
            return 1.0D;
        }
        if (value < MIN_COST_MULTIPLIER) {
            return MIN_COST_MULTIPLIER;
        }
        if (value > MAX_COST_MULTIPLIER) {
            return MAX_COST_MULTIPLIER;
        }
        return value;
    }
}
