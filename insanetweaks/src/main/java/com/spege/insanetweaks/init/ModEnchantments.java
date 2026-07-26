package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentSentientCodex;
import com.spege.insanetweaks.enchant.EnchantmentSwiftPicking;

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
 * <p>Runtime logic for Sentient Codex lives in the Forge-bus SentientCodexHandler, registered in
 * {@code InsaneTweaksMod#init} under the same {@code modules.enableSentientCodex} gate.
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModEnchantments {

    public static EnchantmentSentientCodex SENTIENT_CODEX;
    public static EnchantmentSwiftPicking SWIFT_PICKING;

    @SubscribeEvent
    public static void registerEnchantments(RegistryEvent.Register<Enchantment> event) {
        // Swift Picking is registered UNCONDITIONALLY, unlike Sentient Codex below: gating a
        // registry object on a config flag means turning the flag off deletes the entry from an
        // existing world. modules.enableAutoLockPicker gates the picker's behaviour instead.
        SWIFT_PICKING = new EnchantmentSwiftPicking();
        event.getRegistry().register(SWIFT_PICKING);

        if (!ModConfig.modules.enableSentientCodex) {
            return;
        }
        SENTIENT_CODEX = new EnchantmentSentientCodex();
        event.getRegistry().register(SENTIENT_CODEX);
    }
}
