package com.spege.insanetweaks.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.util.SpellCastFeedback;

@SuppressWarnings("null")
public class ImmuneBondHandler {

    private static final Map<UUID, Integer> activeBonds = new HashMap<>();
    private static final int BOND_RGB = 0x9646DC;

    public static void applyBond(EntityPlayer caster, EntityLivingBase target, int duration) {
        clearBond(caster);
        activeBonds.put(caster.getUniqueID(), target.getEntityId());
        
        // Remove any existing infection immediately
        if (target.isPotionActive(SRPPotions.COTH_E)) {
            target.removePotionEffect(SRPPotions.COTH_E);
        }
        target.addPotionEffect(new PotionEffect(ModPotions.IMMUNE_BOND, duration, 0, false, true));
        target.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 400, 0, false, false));
        
        NBTTagCompound tags = target.getEntityData();
        if (tags.hasKey("srpcothimmunity")) {
            tags.setInteger("insanetweaks_prev_srpcothimmunity", tags.getInteger("srpcothimmunity"));
        }
        tags.setInteger("srpcothimmunity", 0);
    }

    public static void clearBond(EntityPlayer caster) {
        Integer id = activeBonds.remove(caster.getUniqueID());
        if (id == null) return;
        
        Entity e = caster.world.getEntityByID(id);
        if (e instanceof EntityLivingBase) {
            clearBondOnTarget((EntityLivingBase) e, false);
        }
    }
    
    private static void clearBondOnTarget(EntityLivingBase target, boolean isDeath) {
        target.removePotionEffect(ModPotions.IMMUNE_BOND);
        target.removePotionEffect(SRPPotions.COTH_E);
        
        NBTTagCompound tags = target.getEntityData();
        if (isDeath) {
            tags.removeTag("srpcothimmunity");
        } else {
            if (tags.hasKey("insanetweaks_prev_srpcothimmunity")) {
                tags.setInteger("srpcothimmunity", tags.getInteger("insanetweaks_prev_srpcothimmunity"));
                tags.removeTag("insanetweaks_prev_srpcothimmunity");
            } else {
                tags.setInteger("srpcothimmunity", 1);
            }
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) return;
        // Particles pulse every 40 ticks (2 s); every tick for COTH_E strip is too expensive.
        // But we MUST strip COTH_E quickly — do it every 5 ticks for responsiveness.
        boolean particleTick  = player.ticksExisted % 40 == 0;
        boolean protectTick   = player.ticksExisted % 5  == 0;
        if (!particleTick && !protectTick) return;

        Integer targetId = activeBonds.get(player.getUniqueID());
        if (targetId == null) return;

        Entity e = player.world.getEntityByID(targetId);
        if (!(e instanceof EntityLivingBase)) { 
            activeBonds.remove(player.getUniqueID()); 
            return; 
        }
        EntityLivingBase target = (EntityLivingBase) e;

        if (!target.isEntityAlive() || !target.isPotionActive(ModPotions.IMMUNE_BOND)) {
            activeBonds.remove(player.getUniqueID());
            clearBondOnTarget(target, !target.isEntityAlive());
            return;
        }

        // Actively strip COTH_E every 5 ticks — EPEL_E only prevents new applications;
        // if COTH_E was already applied before the bond, we must remove it manually.
        if (protectTick && target.isPotionActive(SRPPotions.COTH_E)) {
            target.removePotionEffect(SRPPotions.COTH_E);
        }
        
        // Force SRP immunity NBT to stay 0 just in case something stripped it
        if (protectTick && target.getEntityData().getInteger("srpcothimmunity") != 0) {
            target.getEntityData().setInteger("srpcothimmunity", 0);
        }

        // Refresh EPEL_E before it expires
        PotionEffect epel = target.getActivePotionEffect(SRPPotions.EPEL_E);
        if (epel == null || epel.getDuration() < 100) {
            target.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 400, 0, false, false));
        }

        // Violet SRP DOT pulse every 40 ticks (2 s)
        if (particleTick) {
            spawnBondParticles(player.world, target);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        int deadId = event.getEntityLiving().getEntityId();
        UUID casterToClear = null;
        for (Map.Entry<UUID, Integer> entry : activeBonds.entrySet()) {
            if (entry.getValue() == deadId) {
                casterToClear = entry.getKey();
                break;
            }
        }
        if (casterToClear != null) {
            activeBonds.remove(casterToClear);
            clearBondOnTarget(event.getEntityLiving(), true);
        }
    }

    private static void spawnBondParticles(World world, EntityLivingBase target) {
        SpellCastFeedback.srpBurstAt(world, target, 0.5D, SRPEnumParticle.DOT, BOND_RGB, 5,
                (float) Math.max(0.4D, target.width * 0.7D), (float) (target.height * 0.3D), 0.0F);
    }
}
