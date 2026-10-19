package com.spege.insanetweaks.anchor;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Pulls a mob that came out of a Petrified Anchor back next to it when it strays further than
 * {@code leashRadius}: the third layer of the Sunken Arcane Sanctum's seal, and a guarantee for
 * hand-built vaults too. Checked every 40 ticks, server only, tagged entities only.
 */
public class AnchorLeashHandler {

    private static final int CHECK_INTERVAL = 40;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % CHECK_INTERVAL != 0 || !(entity instanceof EntityLiving)) {
            return;
        }
        int radius = ModConfig.entities.petrifiedAnchor.leashRadius;
        if (radius <= 0) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        if (entity.getDistanceSq(home) <= (double) radius * radius) {
            return;
        }
        double x = home.getX() + 0.5D;
        double y = home.getY() + 1.0D;
        double z = home.getZ() + 0.5D;
        if (!entity.attemptTeleport(x, y, z)) {
            entity.setPositionAndUpdate(x, y, z);
        }
        ((EntityLiving) entity).getNavigator().clearPath();
    }
}
