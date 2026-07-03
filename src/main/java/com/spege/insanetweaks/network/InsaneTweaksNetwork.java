package com.spege.insanetweaks.network;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class InsaneTweaksNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(InsaneTweaksMod.MODID);
    private static boolean initialized;

    private InsaneTweaksNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        CHANNEL.registerMessage(PacketSentinelCommand.Handler.class, PacketSentinelCommand.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PacketThrallCommand.Handler.class, PacketThrallCommand.class, 1, Side.SERVER);
        CHANNEL.registerMessage(PacketOpenSentinelLoot.Handler.class, PacketOpenSentinelLoot.class, 3, Side.CLIENT);
    }
}
