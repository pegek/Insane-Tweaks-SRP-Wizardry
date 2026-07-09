package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Corrupted Seed Fragment drops from high-tier parasites (configurable prefix list),
 * ONLY when the killing player wears the Blessed Ring. Registered when both
 * enableBaubleFruits and SRP are present.
 */
public class CorruptedFragmentDropHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        Entity killed = event.getEntity();
        if (killed.world.isRemote) return;
        if (!(killed instanceof EntityParasiteBase)) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer killer = (EntityPlayer) event.getSource().getTrueSource();
        if (!EnigmaticLegacyCompat.isWearingBlessedRing(killer)) return;

        ResourceLocation key = EntityList.getKey(killed);
        if (key == null || !isHighTier(key.toString())) return;

        if (killed.world.rand.nextDouble() >= ModConfig.tweaks.fragmentDropChance) return;

        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(ModItems.CORRUPTED_SEED_FRAGMENT)));
    }

    private static boolean isHighTier(String registryName) {
        for (String prefix : ModConfig.tweaks.fragmentDropEntities) {
            if (prefix != null && !prefix.isEmpty() && registryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
