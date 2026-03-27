package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.potions.PotionCleanse;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 
 * Registers all custom potion effects for InsaneTweaks.
 * Uses @Mod.EventBusSubscriber so the registry event fires automatically
 * during the pre-init phase — before any handler in FMLInitializationEvent
 * could try to reference these potions.
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModPotions {

    /**
     * INSPIRED BY "cure" effect from "potioncore" mod.
     * Cleanse — applied by the Living/Sentient Armor Hardcap.
     * 10 ticks of complete damage immunity + instant 2 HP heal at trigger.
     */
    public static PotionCleanse CLEANSE;

    @SubscribeEvent
    public static void registerPotions(RegistryEvent.Register<Potion> event) {
        CLEANSE = (PotionCleanse) new PotionCleanse()
                .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "cleanse"));
        event.getRegistry().register(CLEANSE);
    }
}
