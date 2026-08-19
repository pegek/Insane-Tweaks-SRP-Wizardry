package com.spege.manacore.client;

import com.spege.manacore.CommonProxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // ManaHudRenderer carries a class-level @SideOnly(Side.CLIENT), which makes `new` on it
        // fatal on a dedicated server: Forge's SideTransformer throws at class load regardless of
        // what the constructor does. ClientProxy is the only place the server never loads, so this
        // registration belongs here and nowhere else - never in the @Mod class.
        MinecraftForge.EVENT_BUS.register(new ManaHudRenderer());
    }
}
