package com.spege.insanetweaks.events;

import java.util.List;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPAdapted;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPPrimitive;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("null")
public class ParasiteShroudEventHandler {

    public static final String SHROUD_TICKS_KEY = "InsaneTweaksParasiteShroudTicks";

    private static final double SHROUD_HORIZONTAL_RADIUS = 18.0D;
    private static final double SHROUD_VERTICAL_RADIUS = 8.0D;
    private static final int PRIMITIVE_CLEAR_INTERVAL = 8;
    private static final int OTHER_CLEAR_INTERVAL = 18;
    private static final double PRIMITIVE_STICKY_RANGE_SQ = 9.0D;
    private static final double OTHER_STICKY_RANGE_SQ = 36.0D;

    public static void applyShroud(EntityPlayer player, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        int current = data.getInteger(SHROUD_TICKS_KEY);
        data.setInteger(SHROUD_TICKS_KEY, Math.max(current, durationTicks));
    }

    public static boolean hasShroud(EntityPlayer player) {
        return player != null && player.getEntityData().getInteger(SHROUD_TICKS_KEY) > 0;
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        int ticksLeft = data.getInteger(SHROUD_TICKS_KEY);
        if (ticksLeft <= 0) {
            return;
        }

        if (ticksLeft <= 1) {
            data.removeTag(SHROUD_TICKS_KEY);
            return;
        }

        data.setInteger(SHROUD_TICKS_KEY, ticksLeft - 1);
        this.disruptNearbyParasites(player);
    }

    private void disruptNearbyParasites(EntityPlayer player) {
        AxisAlignedBB area = player.getEntityBoundingBox().grow(SHROUD_HORIZONTAL_RADIUS, SHROUD_VERTICAL_RADIUS,
                SHROUD_HORIZONTAL_RADIUS);
        List<EntityParasiteBase> parasites = player.world.getEntitiesWithinAABB(EntityParasiteBase.class, area);

        for (EntityParasiteBase parasite : parasites) {
            if (parasite == null || !parasite.isEntityAlive()) {
                continue;
            }

            EntityLivingBase attackTarget = parasite.getAttackTarget();
            EntityLivingBase revengeTarget = parasite.getRevengeTarget();
            if (attackTarget != player && revengeTarget != player) {
                continue;
            }

            boolean primitiveOrAdapted = parasite instanceof EntityPPrimitive || parasite instanceof EntityPAdapted;
            int interval = primitiveOrAdapted ? PRIMITIVE_CLEAR_INTERVAL : OTHER_CLEAR_INTERVAL;
            if ((player.ticksExisted + parasite.getEntityId()) % interval != 0) {
                continue;
            }

            double distanceSq = parasite.getDistanceSq(player);
            if (primitiveOrAdapted) {
                if (distanceSq <= PRIMITIVE_STICKY_RANGE_SQ && parasite.getRNG().nextFloat() < 0.45F) {
                    continue;
                }
            } else if (distanceSq <= OTHER_STICKY_RANGE_SQ && parasite.getRNG().nextFloat() < 0.7F) {
                continue;
            }

            clearTargeting(parasite);
        }
    }

    private static void clearTargeting(EntityParasiteBase parasite) {
        parasite.setAttackTarget(null);
        parasite.setRevengeTarget(null);
        if (parasite instanceof EntityLiving) {
            ((EntityLiving) parasite).getNavigator().clearPath();
        }
    }
}
