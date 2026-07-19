package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentGrimoire;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Registers all custom enchantments for InsaneTweaks.
 * Uses @Mod.EventBusSubscriber so the registry event fires automatically on the MOD bus
 * during pre-init - the enchantment registry freezes before FMLInitializationEvent, so
 * this must be a mod-bus registration (mirrors {@link ModPotions}).
 *
 * <p>Runtime logic for Grimoire lives in the Forge-bus GrimoireHandler, registered in
 * {@code InsaneTweaksMod#init} under the same {@code modules.enableGrimoire} gate.
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModEnchantments {

    public static EnchantmentGrimoire GRIMOIRE;

    @SubscribeEvent
    public static void registerEnchantments(RegistryEvent.Register<Enchantment> event) {
        if (!ModConfig.modules.enableGrimoire) {
            return;
        }
        GRIMOIRE = new EnchantmentGrimoire();
        event.getRegistry().register(GRIMOIRE);
    }
}
