package com.spege.insanetweaks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import com.spege.insanetweaks.events.LivingDeathEventHandler;
import com.spege.insanetweaks.commands.CommandBackupCursed;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityItemIndestructible;

// For future reference: dependencies = "...;required-after:swparasites;required-after:potioncore;required-after:enigmaticlegacy"
@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION, dependencies = "required-after:forge@[14.23.5.2860,);required-after:somanyenchantments;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;required-after:swparasites;after:srpextra")
public class InsaneTweaksMod {
    public static final String MODID = "insanetweaks";
    public static final String NAME = "Insane Tweaks";
    public static final String VERSION = "1.0.5";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register Internal Entities
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "indestructible_item"),
                EntityItemIndestructible.class, "indestructible_item", 99, this, 64, 20, true);

        if (com.spege.insanetweaks.config.ModConfig.enableCurseOfPossessionPatch) {
            MinecraftForge.EVENT_BUS.register(new LivingDeathEventHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.enableCustomCores) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CustomCoresEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CoreTooltipHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeHitHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GoldenBookEventHandler());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBackupCursed());
    }
}
