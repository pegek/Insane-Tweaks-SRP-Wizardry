package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.InfernalMobsCompat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Every InfernalMobs elite killed with player credit drops 1-2 (configurable)
 * ebwizardry:spectral_dust of a random element. Registered only when InfernalMobs
 * is loaded AND interactions.enableInfernalDustDrops is on.
 *
 * spectral_dust metadata = Element ordinal: 1 fire, 2 ice, 3 lightning,
 * 4 necromancy, 5 earth, 6 sorcery, 7 healing (0 = MAGIC, deliberately excluded —
 * it has no imbuement use).
 */
public class InfernalDustDropHandler {

    private static final int[] ELEMENT_METAS = {1, 2, 3, 4, 5, 6, 7};

    private static Item spectralDust;
    private static boolean lookedUp = false;
    private static boolean lookupFailed = false;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        EntityLivingBase killed = event.getEntityLiving();
        if (killed.world.isRemote || lookupFailed) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;
        if (!InfernalMobsCompat.isRare(killed)) return;

        if (!lookedUp) {
            spectralDust = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", "spectral_dust"));
            lookedUp = true;
            if (spectralDust == null) {
                lookupFailed = true;
                InsaneTweaksMod.LOGGER.warn(
                        "[InsaneTweaks] ebwizardry:spectral_dust not found — infernal dust drops disabled.");
                return;
            }
        }

        int min = ModConfig.interactions.infernalDustMin;
        int max = Math.max(min, ModConfig.interactions.infernalDustMax);
        int count = min + (max > min ? killed.world.rand.nextInt(max - min + 1) : 0);
        if (count <= 0) return;

        int meta = ELEMENT_METAS[killed.world.rand.nextInt(ELEMENT_METAS.length)];
        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(spectralDust, count, meta)));
    }
}
