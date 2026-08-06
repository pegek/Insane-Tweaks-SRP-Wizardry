package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Server-side half of the Coiled Spring trait: the fall-damage waiver.
 *
 * <p>The leap itself is applied on the client (see {@code ChargeJumpClientHandler}) because
 * {@code EntityLivingBase.jump()} only runs there for a player — the server takes the
 * player's vertical motion from movement packets. Fall damage is the opposite: it is
 * computed server-side in {@code EntityPlayerMP.fall}, so the client has to tell the server
 * a charged leap happened.
 *
 * <p>The armed window lives in the player's own entity NBT rather than a static map: it dies
 * with the entity, survives a dimension change, and cannot leak on logout.
 */
public class ChargeJumpHandler {

    /** World time after which the waiver has expired. */
    private static final String NBT_UNTIL = "insanetweaks_chargejump_until";
    /** Charge fraction actually spent on the leap, 0..1. */
    private static final String NBT_CHARGE = "insanetweaks_chargejump_charge";

    /**
     * Arms the fall-damage waiver. Called from the packet handler on the server and directly
     * on the client so single-player prediction matches.
     *
     * @param charge charge fraction spent, clamped to 0..1 by the caller
     */
    public static void armFallProtection(EntityPlayer player, float charge) {
        NBTTagCompound data = player.getEntityData();
        data.setLong(NBT_UNTIL, player.world.getTotalWorldTime() + ModConfig.chargeJump.fallProtectTicks);
        data.setFloat(NBT_CHARGE, charge);
    }

    private static void disarm(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        data.removeTag(NBT_UNTIL);
        data.removeTag(NBT_CHARGE);
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(NBT_UNTIL)) {
            return;
        }

        // getTotalWorldTime is per-dimension, so a cross-dimension leap simply expires the
        // waiver instead of comparing incomparable clocks.
        if (player.world.getTotalWorldTime() > data.getLong(NBT_UNTIL)) {
            disarm(player);
            return;
        }

        float charge = data.getFloat(NBT_CHARGE);
        disarm(player); // One landing per leap.

        double reduction = ModConfig.chargeJump.fallDamageReduction * charge;
        if (reduction <= 0.0D) {
            return;
        }
        if (reduction >= 1.0D) {
            event.setDistance(0.0F);
            return;
        }
        event.setDistance((float) (event.getDistance() * (1.0D - reduction)));
    }
}
