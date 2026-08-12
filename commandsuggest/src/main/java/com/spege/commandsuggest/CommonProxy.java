package com.spege.commandsuggest;

import com.spege.commandsuggest.net.PacketHandler;
import com.spege.commandsuggest.server.LoginHandler;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Strona wspolna. Nie wolno tu wpisac ani jednego typu z {@code net.minecraft.client}. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        PacketHandler.register();
    }

    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new LoginHandler());
    }
}
