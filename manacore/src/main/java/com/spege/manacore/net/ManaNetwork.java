package com.spege.manacore.net;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ManaNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(ManaCoreMod.MODID);

    private ManaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(PacketManaSync.Handler.class, PacketManaSync.class, 0, Side.CLIENT);
    }

    public static void sync(EntityPlayerMP player) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        CHANNEL.sendTo(new PacketManaSync(pool.getCurrent()), player);
        pool.setDirty(false);
    }
}
