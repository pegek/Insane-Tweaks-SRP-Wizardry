package com.spege.srpwizcore.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.CqrIntegrationCategory;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import team.cqr.cqrepoured.world.structure.protection.ProtectedRegion;
import team.cqr.cqrepoured.world.structure.protection.ProtectedRegionManager;

/**
 * Experimental siege digging (user request 2026-08-01): a CQR dungeon mob that cannot path
 * to its player target slowly chews through PLAYER-PLACED blocks. "Player-placed" is exact,
 * not heuristic: the dungeon's {@link ProtectedRegion} keeps a per-block protection-state
 * grid and {@code isBreakable(pos)} is true precisely for non-original positions — an
 * original dungeon wall is never touched, only what appeared after generation inside the
 * region. The mob and the block must sit in the same region ("their" dungeon).
 *
 * <p>Trigger conditions per mob (checked every 5 ticks, only mobs with a live player
 * target): navigator reports no path (walled off), target within 16 blocks. The dig scans
 * feet/eye level up to {@code siegeDigRange} blocks toward the target, then breaks the
 * block after {@code siegeDigTicksPerBlock} ticks with vanilla crack overlay + hit sounds.
 * Containers (any tile entity) and blocks harder than {@code siegeDigMaxHardness} are
 * never dug. All flags read live; the whole handler is event-driven (no tick cost for
 * non-CQR entities or idle mobs).
 */
public class CqrSiegeDigHandler {

    /** How often (ticks) a digging mob advances its progress. */
    private static final int CHECK_INTERVAL = 5;
    private static final double MAX_TARGET_DIST_SQ = 16.0D * 16.0D;

    private static final class DigState {
        BlockPos pos;
        int progress;
    }

    /** entityId -> current dig. Bounded defensively — reset costs only dig progress. */
    private static final Map<Integer, DigState> DIGS = new HashMap<>();

    private static long blocksDug;

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        EntityLivingBase ent = event.getEntityLiving();
        World world = ent.world;
        if (world.isRemote || !(ent instanceof EntityLiving)) {
            return;
        }
        if (!ent.getClass().getName().startsWith("team.cqr.")) {
            return;
        }
        CqrIntegrationCategory cfg = SrpWizCoreConfig.cqrIntegration;
        if (!cfg.siegeDigEnabled) {
            return;
        }
        if (ent.ticksExisted % CHECK_INTERVAL != 0) {
            return;
        }
        EntityLiving mob = (EntityLiving) ent;
        EntityLivingBase target = mob.getAttackTarget();
        if (!(target instanceof EntityPlayer) || !target.isEntityAlive()
                || mob.getDistanceSq(target) > MAX_TARGET_DIST_SQ
                || !mob.getNavigator().noPath()) {
            abandon(world, mob);
            return;
        }

        try {
            DigState state = DIGS.get(Integer.valueOf(mob.getEntityId()));
            if (state != null && !isDiggable(world, mob, state.pos)) {
                abandon(world, mob);
                state = null;
            }
            if (state == null) {
                BlockPos found = findDigTarget(world, mob, target, cfg);
                if (found == null) {
                    return;
                }
                if (DIGS.size() > 512) {
                    // Leak backstop (despawned diggers): brutal but harmless.
                    DIGS.clear();
                }
                state = new DigState();
                state.pos = found;
                DIGS.put(Integer.valueOf(mob.getEntityId()), state);
            }

            state.progress += CHECK_INTERVAL;
            int total = Math.max(CHECK_INTERVAL, cfg.siegeDigTicksPerBlock);
            mob.swingArm(EnumHand.MAIN_HAND);
            IBlockState bs = world.getBlockState(state.pos);
            world.playSound(null, state.pos,
                    bs.getBlock().getSoundType(bs, world, state.pos, mob).getHitSound(),
                    mob.getSoundCategory(), 0.5F, 0.8F);
            if (state.progress >= total) {
                world.sendBlockBreakProgress(mob.getEntityId(), state.pos, -1);
                world.destroyBlock(state.pos, cfg.siegeDigDrops);
                DIGS.remove(Integer.valueOf(mob.getEntityId()));
                blocksDug++;
                if (cfg.debugLogging || blocksDug == 1L) {
                    SrpWizCore.LOGGER.info(
                            "[srpwizcore] cqr siege dig: {} broke player block at {} (total dug: {})",
                            mob.getClass().getSimpleName(), state.pos, Long.valueOf(blocksDug));
                }
            } else {
                world.sendBlockBreakProgress(mob.getEntityId(), state.pos,
                        state.progress * 10 / total - 1);
            }
        } catch (Throwable t) {
            // Experimental mechanic must never take a mob tick down with it.
            DIGS.remove(Integer.valueOf(mob.getEntityId()));
            SrpWizCore.LOGGER.error("[srpwizcore] cqr siege dig failed: {}", t.toString());
        }
    }

    /** Feet then eye level, 1..range blocks toward the target; first diggable wins. */
    private static BlockPos findDigTarget(World world, EntityLiving mob,
            EntityLivingBase target, CqrIntegrationCategory cfg) {
        double dx = target.posX - mob.posX;
        double dz = target.posZ - mob.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01D) {
            return null;
        }
        dx /= len;
        dz /= len;
        for (int i = 1; i <= cfg.siegeDigRange; i++) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(mob.posX + dx * i, mob.posY + dy,
                        mob.posZ + dz * i);
                if (isDiggable(world, mob, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Diggable = non-air, sane hardness, no tile entity, inside a protected region that
     * ALSO contains the mob, and breakable (= NOT an original dungeon block) in every
     * region covering it.
     */
    private static boolean isDiggable(World world, EntityLiving mob, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock().isAir(state, world, pos)) {
            return false;
        }
        float hardness = state.getBlockHardness(world, pos);
        if (hardness < 0.0F || hardness > SrpWizCoreConfig.cqrIntegration.siegeDigMaxHardness) {
            return false;
        }
        if (world.getTileEntity(pos) != null) {
            return false;
        }
        List<ProtectedRegion> regions = ProtectedRegionManager.getInstance(world)
                .getProtectedRegionsAt(pos);
        if (regions == null || regions.isEmpty()) {
            return false;
        }
        boolean mobInside = false;
        BlockPos mobPos = new BlockPos(mob);
        for (ProtectedRegion region : regions) {
            if (!region.isBreakable(pos)) {
                return false;
            }
            if (region.isInsideProtectedRegion(mobPos)) {
                mobInside = true;
            }
        }
        return mobInside;
    }

    private static void abandon(World world, EntityLiving mob) {
        DigState state = DIGS.remove(Integer.valueOf(mob.getEntityId()));
        if (state != null) {
            world.sendBlockBreakProgress(mob.getEntityId(), state.pos, -1);
        }
    }
}
