package com.spege.insanetweaks.events;

import java.util.List;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.dhanantry.scapeandrunparasites.entity.EntityParasiticScent;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPPrimitive;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.util.SpellCastFeedback;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("null")
public class ParasiteShroudEventHandler {

    public static final String SHROUD_TICKS_KEY = "InsaneTweaksParasiteShroudTicks";
    public static final String SHROUD_TIER_KEY = "InsaneTweaksParasiteShroudTier";

    private static final double SHROUD_HORIZONTAL_RADIUS = 34.0D;
    private static final double SHROUD_VERTICAL_RADIUS = 11.0D;
    private static final double SCENT_HORIZONTAL_RADIUS = 67.0D;
    private static final double SCENT_VERTICAL_RADIUS = 17.0D;
    private static final int DISRUPT_INTERVAL = 7;
    private static final float DISRUPT_CHANCE = 0.7F;

    private static final int EXPIRY_RGB = 0x9A9A9A;
    private static final int BREAK_RGB = 0xC81E1E;

    public static void applyShroud(EntityPlayer player, int durationTicks, int tier) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        int current = data.getInteger(SHROUD_TICKS_KEY);
        data.setInteger(SHROUD_TICKS_KEY, Math.max(current, durationTicks));
        // Recasting never downgrades an active full shroud.
        data.setInteger(SHROUD_TIER_KEY, Math.max(data.getInteger(SHROUD_TIER_KEY), tier));
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
            endShroud(player, data, true);
            return;
        }

        data.setInteger(SHROUD_TICKS_KEY, ticksLeft - 1);

        if (player.ticksExisted % DISRUPT_INTERVAL != 0) {
            return;
        }

        this.disruptNearbyScents(player);
        this.disruptNearbyParasites(player, data.getInteger(SHROUD_TIER_KEY) >= 2);
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player.world.isRemote) {
            return;
        }

        NBTTagCompound data = player.getEntityData();
        if (data.getInteger(SHROUD_TICKS_KEY) <= 0) {
            return;
        }

        endShroud(player, data, false);
    }

    private static void endShroud(EntityPlayer player, NBTTagCompound data, boolean natural) {
        data.removeTag(SHROUD_TICKS_KEY);
        data.removeTag(SHROUD_TIER_KEY);

        if (natural) {
            SpellCastFeedback.srpBurstAt(player.world, player, 0.5D,
                    SRPEnumParticle.GCLOUD, EXPIRY_RGB, 10, 0.5F, 0.6F, 0.01F);
        } else {
            SpellCastFeedback.srpBurstAt(player.world, player, 0.5D,
                    SRPEnumParticle.FLASH, BREAK_RGB, 6, 0.4F, 0.5F, 0.02F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F);
        }
    }

    private void disruptNearbyScents(EntityPlayer player) {
        player.removePotionEffect(SRPPotions.PREY_E);
        player.removePotionEffect(SRPPotions.SPOT_E);

        AxisAlignedBB area = player.getEntityBoundingBox().grow(SCENT_HORIZONTAL_RADIUS, SCENT_VERTICAL_RADIUS,
                SCENT_HORIZONTAL_RADIUS);
        List<EntityParasiticScent> scents = player.world.getEntitiesWithinAABB(EntityParasiticScent.class, area);

        for (EntityParasiticScent scent : scents) {
            if (scent == null || !scent.isEntityAlive()) {
                continue;
            }

            EntityLivingBase target = scent.getTargetToKill();
            if (target != player) {
                continue;
            }

            if (player.getRNG().nextFloat() > DISRUPT_CHANCE) {
                continue;
            }

            scent.setDead();
        }
    }

    private void disruptNearbyParasites(EntityPlayer player, boolean fullShroud) {
        AxisAlignedBB area = player.getEntityBoundingBox().grow(SHROUD_HORIZONTAL_RADIUS, SHROUD_VERTICAL_RADIUS,
                SHROUD_HORIZONTAL_RADIUS);
        List<EntityParasiteBase> parasites = player.world.getEntitiesWithinAABB(EntityParasiteBase.class, area);

        for (EntityParasiteBase parasite : parasites) {
            if (parasite == null || !parasite.isEntityAlive()) {
                continue;
            }

            // Tier 1 only hides from primitive-stage parasites; tier 2 from all.
            if (!fullShroud && !(parasite instanceof EntityPPrimitive)) {
                continue;
            }

            EntityLivingBase attackTarget = parasite.getAttackTarget();
            EntityLivingBase revengeTarget = parasite.getRevengeTarget();
            if (attackTarget != player && revengeTarget != player) {
                continue;
            }

            if (player.getRNG().nextFloat() > DISRUPT_CHANCE) {
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
