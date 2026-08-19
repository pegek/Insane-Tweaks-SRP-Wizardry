package com.spege.manacore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ManaCoreMod.MODID, name = ManaCoreMod.NAME, version = ManaCoreMod.VERSION,
        acceptableRemoteVersions = "*")
public class ManaCoreMod {

    public static final String MODID = "manacore";
    public static final String NAME = "Mana Core";
    /** MUST be kept in sync by hand with `version` in build.gradle - the manifest is not visible to @Mod. */
    public static final String VERSION = "0.4.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.spege.manacore.client.ClientProxy",
            serverSide = "com.spege.manacore.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        com.spege.manacore.cap.ManaCapabilities.register();
        com.spege.manacore.net.ManaNetwork.register();
        proxy.preInit(event);
        LOGGER.info("[ManaCore] preInit done");
    }

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.manacore.command.CommandMana());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        com.spege.manacore.compat.wizardryutils.WizardryUtilsBridge.init();

        if (net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry")
                && com.spege.manacore.config.ManaCoreConfig.ebw.enabled) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.ebw.EbwSpellCostHandler());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.ebw.WandUpgradeBridge());
            LOGGER.info("[ManaCore] EBW bridge registered");
        } else {
            LOGGER.info("[ManaCore] EBW bridge NOT registered (mod present={}, enabled={})",
                    Boolean.valueOf(net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry")),
                    Boolean.valueOf(com.spege.manacore.config.ManaCoreConfig.ebw.enabled));
        }

        if (net.minecraftforge.fml.common.Loader.isModLoaded("xat")
                && com.spege.manacore.config.ManaCoreConfig.tab.enabled) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.tab.TabManaItemHandler());
            LOGGER.info("[ManaCore] TaB bridge registered");
        } else {
            LOGGER.info("[ManaCore] TaB bridge NOT registered (mod present={}, enabled={})",
                    Boolean.valueOf(net.minecraftforge.fml.common.Loader.isModLoaded("xat")),
                    Boolean.valueOf(com.spege.manacore.config.ManaCoreConfig.tab.enabled));
        }

        proxy.init(event);
    }
}
