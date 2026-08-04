package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SanctuaryRegionHelper {

    private SanctuaryRegionHelper() {}

    public static boolean isDimensionBlacklisted(World world) {
        int dim = world.provider.getDimension();
        for (int d : ModConfig.sanctuary.dimensionBlacklist) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }

    /** True when (x,z) in `world` lies inside any active sanctuary and the module is not gated off there. */
    public static boolean isProtected(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInside(x, z);
    }

    public static boolean isProtected(World world, BlockPos pos) {
        return pos != null && isProtected(world, pos.getX(), pos.getZ());
    }

    private static final String SRP_PARASITE_BASE =
            "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";

    /**
     * SRP's {@code EntityParasiteBase}, resolved once, or null when SRParasites is absent.
     *
     * <p>Resolved by name rather than referenced as a class literal on purpose: this helper is
     * reached from the sanctuary handlers and from mixins, and must stay loadable in a pack without
     * SRParasites. A literal would put the type in the constant pool and turn a missing mod into a
     * {@code NoClassDefFoundError}. That is the whole reason the name lived here as a string.
     *
     * <p>{@code initialize = false} because {@link Class#isInstance} never needs it - resolving the
     * type must not drag SRP's static initialisers in at whatever moment we first ask.
     */
    private static final Class<?> SRP_PARASITE_CLASS = resolveParasiteBase();

    private static Class<?> resolveParasiteBase() {
        try {
            return Class.forName(SRP_PARASITE_BASE, false, SanctuaryRegionHelper.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            // Expected when SRParasites is not installed. Only worth shouting about when it IS,
            // because then the class was renamed or repackaged by an SRP update and every sanctuary
            // parasite check has just gone quiet - which would otherwise look like "the dome does
            // nothing" with no error anywhere.
            if (net.minecraftforge.fml.common.Loader
                    .isModLoaded(com.spege.insanetweaks.InsaneTweaksMod.SRP_MODID)) {
                com.spege.insanetweaks.InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] SRParasites is loaded but " + SRP_PARASITE_BASE
                        + " could not be resolved. Every sanctuary parasite check will answer false.");
            }
            return null;
        } catch (Exception e) {
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.error(
                    "[InsaneTweaks] Failed to resolve " + SRP_PARASITE_BASE, e);
            return null;
        }
    }

    /**
     * True if the entity's class chain includes SRP's EntityParasiteBase (covers SRP, SRPExtra,
     * SimWizard).
     *
     * <p>🚨 This is one of the hottest methods in the mod: five handlers call it from
     * {@code LivingUpdateEvent} and friends, i.e. for every living entity in the world on every
     * tick. It used to walk the entity's whole superclass chain comparing {@code c.getName()}
     * against a 68-character string, so an ordinary cow cost eight iterations and eight string
     * comparisons per tick. A Flare profile from 2026-08-01 put it at 1064 ms of a 201 s server
     * thread (0.53%), the single most expensive method in this mod.
     *
     * <p>{@code isInstance} against the resolved type replaces all of that. {@code EntityParasiteBase}
     * is a class rather than an interface, so the JVM answers it with a primary-supertype check -
     * one load and one comparison at a fixed offset - instead of a loop.
     */
    public static boolean isSrpParasite(net.minecraft.entity.Entity e) {
        return e != null && SRP_PARASITE_CLASS != null && SRP_PARASITE_CLASS.isInstance(e);
    }

    /**
     * Config escape hatch for anything that must not be removed by a sanctuary, e.g. to keep a boss
     * fight from being trivialised by a nearby dome. A bare entry with no namespace matches that
     * path in any namespace, so {@code overseer} covers the SRParasites, SRPExtra and SW: Parasites
     * variants at once.
     *
     * <p>Shared by the two mechanisms that make a parasite disappear outright: dwell execution
     * ({@code SanctuaryPurgeFireHandler}) and the join veto ({@code SanctuarySpawnVetoHandler}).
     * One list rather than two, because the intent is identical - "never delete this entity".
     * Purge fire itself is not gated on it: an exempt boss still burns, it just cannot be deleted.
     */
    public static boolean isExemptEntity(net.minecraft.entity.Entity e) {
        String[] ids = ModConfig.sanctuary.dwellExecutionExemptIds;
        if (ids == null || ids.length == 0) {
            return false;
        }
        ResourceLocation key = EntityList.getKey(e);
        if (key == null) {
            return false;
        }
        String full = key.toString();
        String path = key.getResourcePath();
        for (String raw : ids) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (id.indexOf(':') < 0 ? path.equals(id) : full.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** True when (x,z) is inside any active sanctuary within min(regionRadius, purgeFireRadiusCap). */
    public static boolean isInPurgeRange(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInsideCapped(x, z, ModConfig.sanctuary.purgeFireRadiusCap);
    }
}
