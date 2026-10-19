package com.spege.srpwizcore.network;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.devspeed.PacketDevSpeed;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * srpwizcore's own network channel (it never had one before 1.16.0).
 *
 * <p>Registered unconditionally in preInit, regardless of any config flag: discriminators have
 * to match on both ends, and the client and the server do not necessarily share a config file.
 * Append new messages with the next free discriminator; never renumber.
 */
public final class SrpWizCoreNetwork {

    private static SimpleNetworkWrapper channel;

    private SrpWizCoreNetwork() {
    }

    public static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(SrpWizCore.MODID);
        channel.registerMessage(PacketDevSpeed.Handler.class, PacketDevSpeed.class, 0, Side.CLIENT);
    }

    public static SimpleNetworkWrapper channel() {
        return channel;
    }
}
