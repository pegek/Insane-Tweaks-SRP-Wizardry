package com.spege.commandsuggest.net;

import com.spege.commandsuggest.CommandSuggest;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Jeden kanal, jeden pakiet. Rejestrowany z {@code CommonProxy.preInit}, po obu stronach. */
public final class PacketHandler {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(CommandSuggest.MODID);

    private PacketHandler() {
    }

    public static void register() {
        CHANNEL.registerMessage(S2CCommandTree.Handler.class, S2CCommandTree.class, 0, Side.CLIENT);
    }
}
