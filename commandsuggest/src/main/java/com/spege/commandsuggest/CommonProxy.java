package com.spege.commandsuggest;

import com.spege.commandsuggest.net.PacketHandler;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Strona wspolna. Nie wolno tu wpisac ani jednego typu z {@code net.minecraft.client}. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        PacketHandler.register();
        // wypelnia zadanie 12 (komenda, login hook)
    }

    public void init(FMLInitializationEvent event) {
    }
}
