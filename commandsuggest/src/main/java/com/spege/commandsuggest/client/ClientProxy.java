package com.spege.commandsuggest.client;

import com.spege.commandsuggest.CommonProxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Wszystkie handlery klienckie rejestrowane TYLKO stad. {@code GuiScreenEvent} jest klasowo
 * {@code @SideOnly(Side.CLIENT)} razem z podklasami, wiec {@code new ChatScreenHandler()} na
 * dedyku bylby crashem przy ladowaniu klasy (SideTransformer), a nie dopiero przy wywolaniu.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new ClientTreeCache.Events());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new ChatScreenHandler());
    }
}
