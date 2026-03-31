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
            // Zmieniono logikę ze skanowania bloków na sprawdzenie gracza - rozwiązanie optymalne!
            // Gracz musi być w promieniu 8 kratek, posiadać trait i trzymać w dłoni Diamond Hoe.
            AxisAlignedBB searchBox = new AxisAlignedBB(event.getPos()).grow(8.0);
            List<EntityPlayer> players = event.getWorld().getEntitiesWithinAABB(EntityPlayer.class, searchBox);

            boolean hasTraitPlayer = false;
            for (EntityPlayer player : players) {
                if (TraitBase.hasTrait(player, "reskillable:farming", "compatskills:adapted_vegetation")) {
                    net.minecraft.item.Item mainhand = player.getHeldItemMainhand().getItem();
                    net.minecraft.item.Item offhand = player.getHeldItemOffhand().getItem();
                    
                    net.minecraft.util.ResourceLocation mainReg = mainhand.getRegistryName();
                    net.minecraft.util.ResourceLocation offReg = offhand.getRegistryName();
                    String mainId = mainReg != null ? mainReg.toString() : "";
                    String offId = offReg != null ? offReg.toString() : "";
                    
                    if (mainId.equals("srparasites:weapon_scythe") || mainId.equals("srparasites:weapon_scythe_sentient") ||
                        offId.equals("srparasites:weapon_scythe") || offId.equals("srparasites:weapon_scythe_sentient")) {
                        hasTraitPlayer = true;
                        break;
                    }
                }
            }

            if (hasTraitPlayer) {
                // Cofa blokadę narzuconą przez SRParasites
                event.setResult(Event.Result.DEFAULT);

                // Powiadomienia graficzne - z racji, że rośnięcie wykonuje się na serwerze (isRemote == false)
                // Musimy wymusić pakiet graficzny dla pobliskich klientów używając WorldServer
                if (!event.getWorld().isRemote && event.getWorld().rand.nextInt(3) == 0) {
                    if (event.getWorld() instanceof net.minecraft.world.WorldServer) {
                        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) event.getWorld();
                        ws.spawnParticle(
                                net.minecraft.util.EnumParticleTypes.VILLAGER_HAPPY,
                                event.getPos().getX() + 0.5D,
                                event.getPos().getY() + 0.5D,
                                event.getPos().getZ() + 0.5D,
                                5,      // Ilość cząsteczek
                                0.3D,   // Offset X
                                0.3D,   // Offset Y
                                0.3D,   // Offset Z
                                0.01D   // Prędkość
                        );
                    }
                }
            }
        }
    }
}
