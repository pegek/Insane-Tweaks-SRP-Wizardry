package com.spege.insanetweaks.anchor;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.worldgen.sanctum.SanctumLocks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps a Petrified Anchor's mob tied to its anchor, every 40 ticks, server only:
 * <ul>
 *   <li>Leash - further than {@code leashRadius}: pulled straight back (hard limit).</li>
 *   <li>Return - no player within {@code returnRadius} for {@code returnSeconds}: teleports home
 *   and heals {@code returnHealFraction} of its missing health.</li>
 *   <li>Death - tells the anchor (if loaded) and the sanctum record (always), which is what
 *   lifts a sanctum's Arcane Lock.</li>
 * </ul>
 */
public class AnchorLeashHandler {

    private static final int CHECK_INTERVAL = 40;
    private static final double HOME_SLACK_SQ = 16.0D;

    private static final Predicate<Entity> NOT_SPECTATOR = new Predicate<Entity>() {
        @Override
        public boolean apply(Entity e) {
            return e instanceof EntityPlayer && !((EntityPlayer) e).isSpectator();
        }
    };

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % CHECK_INTERVAL != 0 || !(entity instanceof EntityLiving)) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        EntitiesCategory.PetrifiedAnchor cfg = ModConfig.entities.petrifiedAnchor;
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        double dist = entity.getDistanceSq(home);
        int radius = cfg.leashRadius;
        if (radius > 0 && dist > (double) radius * radius) {
            sendHome((EntityLiving) entity, home, 0.0D);
            return;
        }
        EntityPlayer near = entity.world.getClosestPlayer(entity.posX, entity.posY, entity.posZ, cfg.returnRadius,
                NOT_SPECTATOR);
        if (near != null) {
            data.setInteger(AnchorLeash.ALONE, 0);
            return;
        }
        int alone = data.getInteger(AnchorLeash.ALONE) + CHECK_INTERVAL;
        if (alone >= cfg.returnSeconds * 20) {
            alone = 0;
            if (dist > HOME_SLACK_SQ) {
                sendHome((EntityLiving) entity, home, cfg.returnHealFraction);
            }
        }
        data.setInteger(AnchorLeash.ALONE, alone);
    }

    private static void sendHome(EntityLiving mob, BlockPos home, double healFraction) {
        MagicParasiteTraits.burst(mob.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 16, 0.4D);
        double x = home.getX() + 0.5D;
        double y = home.getY() + 1.0D;
        double z = home.getZ() + 0.5D;
        if (!mob.attemptTeleport(x, y, z)) {
            mob.setPositionAndUpdate(x, y, z);
        }
        mob.getNavigator().clearPath();
        mob.setAttackTarget(null);
        if (healFraction > 0.0D) {
            mob.heal((float) ((mob.getMaxHealth() - mob.getHealth()) * healFraction));
        }
        MagicParasiteTraits.burst(mob.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 16, 0.4D);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        NBTTagCompound data = entity.getEntityData();
        if (world.isRemote || !data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        if (world.isBlockLoaded(home)) {
            TileEntity te = world.getTileEntity(home);
            if (te instanceof TileEntityPetrifiedAnchor) {
                ((TileEntityPetrifiedAnchor) te).markMobGone();
            }
        }
        if (data.hasKey(AnchorLeash.SANCTUM)) {
            SanctumLocks.anchorDone(world, data.getLong(AnchorLeash.SANCTUM), home);
        }
    }
}
