package com.spege.insanetweaks.entities.sapling;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * Brings the hive to the Corrupted Sapling: pulls nearby parasites onto it and spawns the waves.
 *
 * <p>Targeting uses SRP's own {@code EntityParasiteBase.setAttackTarget(target, ticks)}, which sets
 * the target and locks it against changes for {@code ticks} ({@code targetNewCool}). No mixin.
 *
 * <p>Spawning goes through {@code world.spawnEntity}, which bypasses {@code CheckSpawn} and so
 * srpwizmixins' parasite origin gate — the sapling's dimension blacklist is what keeps dimension
 * 150 free of parasites.
 *
 * <p>🚨 Every SRP type stays inside {@link Srp}. Its methods are only reached after
 * {@link #srpLoaded()}, and a nested class referenced from a method body resolves lazily, so this
 * class loads fine without SRParasites.
 */
public final class SaplingWaveSpawner {

    /** Spawned wave parasites stay locked on the sapling this long before their own AI may retarget. */
    private static final int SPAWN_LOCK_TICKS = 200;
    private static final int POSITION_ATTEMPTS = 10;
    private static final int VERTICAL_SEARCH = 6;

    private static final Set<String> WARNED_IDS = new HashSet<String>();
    private static Boolean srpLoaded;

    private SaplingWaveSpawner() {
    }

    private static boolean srpLoaded() {
        if (srpLoaded == null) {
            srpLoaded = Boolean.valueOf(Loader.isModLoaded(InsaneTweaksMod.SRP_MODID));
        }
        return srpLoaded.booleanValue();
    }

    /** Living SRP parasites inside {@code box}; 0 without SRParasites. */
    public static int countParasites(World world, AxisAlignedBB box) {
        return srpLoaded() ? Srp.countLiving(world, box) : 0;
    }

    /** Points every living parasite within {@code radius} that is not already on it at {@code target}. */
    public static void lure(EntityLivingBase target, double radius, int lockTicks) {
        if (!srpLoaded()) {
            return;
        }
        Srp.lure(target.world, target.getEntityBoundingBox().grow(radius), target, lockTicks);
    }

    /**
     * Spawns one wave around {@code target}: {@code count} parasites from the phase's pool, plus the
     * phase's elite when {@code elite}. Mobs that find no valid position are skipped.
     *
     * @return how many were actually spawned
     */
    public static int spawnWave(EntityLivingBase target, int phase, int count, boolean elite) {
        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        World world = target.world;
        String[] pool = poolFor(cfg, phase);
        BlockPos center = new BlockPos(target);
        int spawned = 0;
        int skipped = 0;
        for (int i = 0; i < count && pool.length > 0; i++) {
            if (spawnOne(world, pool[world.rand.nextInt(pool.length)], center, target, cfg)) {
                spawned++;
            } else {
                skipped++;
            }
        }
        if (elite) {
            if (spawnOne(world, eliteFor(cfg, phase), center, target, cfg)) {
                spawned++;
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            InsaneTweaksMod.LOGGER.debug("[InsaneTweaks] Corrupted Sapling wave at {}: {} spawned, {} skipped "
                    + "(no valid position or unknown id).", center, Integer.valueOf(spawned), Integer.valueOf(skipped));
        }
        return spawned;
    }

    private static String[] poolFor(BaubleFruitsCategory cfg, int phase) {
        if (phase >= 7) {
            return cfg.saplingWavePoolLate;
        }
        return phase >= 4 ? cfg.saplingWavePoolMid : cfg.saplingWavePoolEarly;
    }

    private static String eliteFor(BaubleFruitsCategory cfg, int phase) {
        if (phase >= 7) {
            return cfg.saplingEliteLate;
        }
        return phase >= 4 ? cfg.saplingEliteMid : cfg.saplingEliteEarly;
    }

    private static boolean spawnOne(World world, String id, BlockPos center, EntityLivingBase target,
            BaubleFruitsCategory cfg) {
        BlockPos pos = findSpawnPos(world, center, world.rand, cfg.saplingSpawnRingMin, cfg.saplingSpawnRingMax);
        if (pos == null) {
            return false;
        }
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
        if (!(entity instanceof EntityLiving)) {
            if (WARNED_IDS.add(id)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Corrupted Sapling wave: '{}' is not a living entity id "
                        + "- skipped. Check the baubleFruits wave pools and elites.", id);
            }
            return false;
        }
        EntityLiving mob = (EntityLiving) entity;
        mob.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                world.rand.nextFloat() * 360.0F, 0.0F);
        mob.onInitialSpawn(world.getDifficultyForLocation(pos), null);
        if (!world.spawnEntity(mob)) {
            return false;
        }
        if (srpLoaded()) {
            Srp.lock(mob, target, SPAWN_LOCK_TICKS);
        }
        return true;
    }

    /** A random standable spot on the ring, outside any sanctuary, or null after 10 misses. */
    @Nullable
    private static BlockPos findSpawnPos(World world, BlockPos center, Random rand, int ringMin, int ringMax) {
        int inner = Math.min(ringMin, ringMax);
        int outer = Math.max(ringMin, ringMax);
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double dist = inner + rand.nextDouble() * (outer - inner);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (isStandable(world, feet) && !SanctuaryRegionHelper.isProtected(world, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    /** Solid top face below, two pass-through non-liquid blocks for the body. */
    private static boolean isStandable(World world, BlockPos feet) {
        BlockPos below = feet.down();
        if (!world.getBlockState(below).isSideSolid(world, below, EnumFacing.UP)) {
            return false;
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos body = feet.up(dy);
            if (SaplingSiteCheck.collides(world, body) || world.getBlockState(body).getMaterial().isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /** The only place SRP types appear. Reached exclusively after {@link #srpLoaded()}. */
    private static final class Srp {
        private Srp() {
        }

        static int countLiving(World world, AxisAlignedBB box) {
            int alive = 0;
            for (com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase p : world.getEntitiesWithinAABB(
                    com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase.class, box)) {
                if (p.isEntityAlive()) {
                    alive++;
                }
            }
            return alive;
        }

        static void lure(World world, AxisAlignedBB box, EntityLivingBase target, int lockTicks) {
            for (com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase p : world.getEntitiesWithinAABB(
                    com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase.class, box)) {
                if (p.isEntityAlive() && p.getAttackTarget() != target) {
                    p.setAttackTarget(target, lockTicks);
                }
            }
        }

        static void lock(Entity mob, EntityLivingBase target, int lockTicks) {
            if (mob instanceof com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) {
                ((com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) mob)
                        .setAttackTarget(target, lockTicks);
            }
        }
    }
}
