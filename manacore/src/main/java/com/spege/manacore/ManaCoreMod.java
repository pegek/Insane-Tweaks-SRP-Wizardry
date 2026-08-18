package com.spege.manacore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ManaCoreMod.MODID, name = ManaCoreMod.NAME, version = ManaCoreMod.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class ManaCoreMod {

    public static final String MODID = "manacore";
    public static final String NAME = "Mana Core";
    /** MUSI byc recznie zsynchronizowana z `version` w build.gradle - manifest nie jest widoczny dla @Mod. */
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.spege.manacore.client.ClientProxy",
            serverSide = "com.spege.manacore.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        LOGGER.info("[ManaCore] preInit done");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
