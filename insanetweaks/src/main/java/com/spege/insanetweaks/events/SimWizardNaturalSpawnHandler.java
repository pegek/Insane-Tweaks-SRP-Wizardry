package com.spege.insanetweaks.events;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.util.SrpPhaseHelper;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Decides whether a naturally picked sim_wizard / sim_battlemage may appear. Spec:
 * docs/superpowers/specs/2026-10-02-sim-wizard-native-spawn-design.md, section 3.
 *
 * <p>🚨 LOWEST, and only ever DENY. If SRP's own CheckSpawn handler sets ALLOW for parasites, a
 * higher priority here would be overwritten by it; at LOWEST we write last. ALLOW is never set
 * either: in Forge 1.12 it skips getCanSpawnHere and isNotColliding, so a wizard could appear in
 * a wall. The Sanctuary veto shares LOWEST and also only denies, so the order between us is moot.
 *
 * <p>Only the natural spawner reaches this with a sim wizard. Assimilation, spawn eggs and
 * /summon go straight to spawnEntity; spawner blocks are let through untouched below.
 *
 * <p>A wizard that passes is marked natural here rather than in SpecialSpawn: the candidate is a
 * throwaway object, so if a later check (light, collision) refuses it, the mark goes with it.
 */
public class SimWizardNaturalSpawnHandler {

    enum Verdict { DIMENSION, PHASE, UNDERFOOT, GROUND, EXCLUSION, PASSED }

    private static final Verdict[] VERDICTS = Verdict.values();

    // CheckSpawn may run on EntityThreading workers in this pack - hence atomics, not ints.
    private static final AtomicIntegerArray COUNTS = new AtomicIntegerArray(VERDICTS.length);
    private static final AtomicLong LAST_LOG = new AtomicLong(0L);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        // Fires for every mob in the game: the type test has to come first.
        if (!(event.getEntityLiving() instanceof EntitySimWizard)) {
            return;
        }
        if (event.getResult() == Event.Result.DENY || event.isSpawner()) {
            return;
        }
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        EntitySimWizard wizard = (EntitySimWizard) event.getEntityLiving();
        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        Verdict verdict = evaluate(world, wizard, event.getX(), event.getY(), event.getZ(), cfg);
        if (cfg.debugLog) {
            record(verdict);
        }
        if (verdict == Verdict.PASSED) {
            wizard.markNaturalSpawn();
        } else {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Cheapest test first: config lookups, then one block read, then 25, then an entity query. */
    static Verdict evaluate(World world, EntitySimWizard wizard, double x, double y, double z,
            EntitiesCategory.NaturalSpawn cfg) {
        if (!dimensionAllowed(world.provider.getDimension(), cfg)) {
            return Verdict.DIMENSION;
        }
        int minPhase = wizard instanceof EntitySimBattlemage ? cfg.battlemageMinPhase : cfg.minPhase;
        if (SrpPhaseHelper.getEvolutionPhase(world) < minPhase) {
            return Verdict.PHASE;
        }
        BlockPos ground = new BlockPos(x, y, z).down();
        if (cfg.requireInfestedUnderfoot && !isInfested(world, ground)) {
            return Verdict.UNDERFOOT;
        }
        if (cfg.minInfestedGround > 0 && !patchInfested(world, ground, cfg.minInfestedGround)) {
            return Verdict.GROUND;
        }
        int r = cfg.exclusionRadius;
        // The candidate is not in the world yet, so it can never find itself here. The query is
        // by class, so a sim_battlemage (a subclass) blocks a sim_wizard and the other way round.
        if (r > 0 && !world.getEntitiesWithinAABB(EntitySimWizard.class,
                new AxisAlignedBB(x - r, y - r, z - r, x + r, y + r, z + r)).isEmpty()) {
            return Verdict.EXCLUSION;
        }
        return Verdict.PASSED;
    }

    private static boolean dimensionAllowed(int dim, EntitiesCategory.NaturalSpawn cfg) {
        boolean listed = false;
        for (int d : cfg.dimensions) {
            if (d == dim) {
                listed = true;
                break;
            }
        }
        return listed == cfg.dimensionWhitelist;
    }

    private static boolean isInfested(World world, BlockPos pos) {
        // The patch can cross into a neighbouring chunk; an unloaded block counts as clean and
        // must never be read (that would load or generate it).
        return world.isBlockLoaded(pos) && SrpPurificationHelper.isSrpInfested(world.getBlockState(pos));
    }

    /** True once {@code needed} of the 5x5 blocks centred on {@code ground} are infested. */
    private static boolean patchInfested(World world, BlockPos ground, int needed) {
        int found = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.setPos(ground.getX() + dx, ground.getY(), ground.getZ() + dz);
                if (isInfested(world, p) && ++found >= needed) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void record(Verdict verdict) {
        COUNTS.incrementAndGet(verdict.ordinal());
        long now = System.currentTimeMillis();
        long last = LAST_LOG.get();
        if (last == 0L) {
            LAST_LOG.compareAndSet(0L, now);
            return;
        }
        if (now - last < 60000L || !LAST_LOG.compareAndSet(last, now)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Verdict v : VERDICTS) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v.name().toLowerCase(Locale.ROOT)).append('=').append(COUNTS.getAndSet(v.ordinal(), 0));
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizardSpawn] last minute: {}", sb);
    }
}
