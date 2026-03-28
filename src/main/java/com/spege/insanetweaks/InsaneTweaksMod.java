package com.spege.insanetweaks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import com.spege.insanetweaks.events.LivingDeathEventHandler;
import com.spege.insanetweaks.commands.CommandBackupCursed;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;
import com.spege.insanetweaks.entities.EntityItemIndestructible;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// For future reference: dependencies = "...;required-after:swparasites;required-after:potioncore;required-after:enigmaticlegacy"
@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION, dependencies = "required-after:forge@[14.23.5.2860,);required-after:somanyenchantments;required-after:ebwizardry;required-after:spartanweaponry;required-after:ancientspellcraft;required-after:swparasites;after:srpextra;after:baubles;after:potioncore;after:compatskills;after:reskillable")
public class InsaneTweaksMod {
    public static final String MODID = "insanetweaks";
    public static final String NAME = "Insane Tweaks";
    public static final String VERSION = "1.0.5";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register Internal Entities
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "indestructible_item"),
                EntityItemIndestructible.class, "indestructible_item", 99, this, 64, 20, true);

        // Fire/lava immunity for all Living and Sentient item drops — registered unconditionally
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.IndestructibleDropHandler());

        if (com.spege.insanetweaks.config.ModConfig.tweaks.enableCurseOfPossessionPatch) {
            MinecraftForge.EVENT_BUS.register(new LivingDeathEventHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CustomCoresEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CoreTooltipHandler());
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeHitHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArmorTooltipHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.AegisTooltipHandler());
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellbladeSoundHandler());
            }
        }

        // GoldenBook is independent of the SRP/EBWizardry bridge  Eregister unconditionally.
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.GoldenBookEventHandler());

        if (!Loader.isModLoaded("srpextra")) {
            net.minecraftforge.fml.common.FMLLog.bigWarning(
                "[InsaneTweaks] SRPExtra is NOT installed! " +
                "Fallback crafting recipes are now active. " +
                "Install SRPExtra for proper balance and game experience.");

            // Also show a warning in-game chat when a player logs in
            MinecraftForge.EVENT_BUS.register(new Object() {
                @SubscribeEvent
                public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
                    event.player.sendMessage(new TextComponentString(
                        "\u00a7e[InsaneTweaks] \u00a7c\u00a7lWarning! \u00a7r\u00a7e" +
                        "SRPExtra is not installed. Fallback recipes are active. " +
                        "Install SRPExtra for proper balance and full game experience."));
                }
            });
        }

        // BaublesEX version check  Elog info about the operating mode
        if (com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits
                && Loader.isModLoaded("baubles")) {
            // Register the legacy persistence handler unconditionally.
            // When BaublesEX is present it does nothing (no legacy bonus stored).
            // When old Baubles is present it re-applies the luck modifier on login/respawn.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.BaubleFruitEventHandler());

            if (!com.spege.insanetweaks.init.ModItems.isBaublesExPresent()) {
                net.minecraftforge.fml.common.ModContainer baubles =
                        Loader.instance().getIndexedModList().get("baubles");
                String installedVersion = baubles != null ? baubles.getVersion() : "unknown";
                LOGGER.info("[InsaneTweaks] Bauble Fruits: Original Baubles v{} detected (not BaublesEX). " +
                        "Running in LEGACY MODE - Ring Fruits will grant +1 Luck instead of +1 Ring slot.",
                        installedVersion);
            }
        }

        if (Loader.isModLoaded("reskillable")) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.skills.EventHandlerSkills());
            LOGGER.info("[InsaneTweaks] Zarejestrowano eventy dla umiejętności Reskillable i CompatSkills.");
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBackupCursed());
    }
}
