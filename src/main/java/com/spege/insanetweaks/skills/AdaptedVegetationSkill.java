package com.spege.insanetweaks.skills;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

public class AdaptedVegetationSkill {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void protectCrops(BlockEvent.CropGrowEvent.Pre event) {
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSkillsModule)
            return;

        if (event.getResult() == Event.Result.DENY) {
            // Szukamy graczy ze skillem Adapted Vegetation w promieniu 16 kratek
            AxisAlignedBB searchBox = new AxisAlignedBB(event.getPos()).grow(40.0);
            List<EntityPlayer> players = event.getWorld().getEntitiesWithinAABB(EntityPlayer.class, searchBox);

            for (EntityPlayer player : players) {
                if (TraitBase.hasTrait(player, "reskillable:farming", "compatskills:adapted_vegetation")) {
                    // Cofa blokadę narzuconą przez SRParasites
                    event.setResult(Event.Result.DEFAULT);

                    // Efekt wizualny ratowania rośliny (Happy Villager particle)
                    if (event.getWorld().isRemote && event.getWorld().rand.nextInt(4) == 0) {
                        event.getWorld().spawnParticle(
                                net.minecraft.util.EnumParticleTypes.VILLAGER_HAPPY,
                                event.getPos().getX() + 0.5D,
                                event.getPos().getY() + 0.5D + (event.getWorld().rand.nextDouble() * 0.5),
                                event.getPos().getZ() + 0.5D,
                                0.0D, 0.0D, 0.0D);
                    }
                    break;
                }
            }
        }
    }
}
