package com.spege.manacore.net;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -&gt; client sync channel for the current-mana value.
 *
 * <p>The channel intentionally exposes two differently-named send paths instead of one:
 * <ul>
 *     <li>{@link #syncNow(EntityPlayerMP)} is a raw, unconditional "send it now" — it always
 *     writes a packet and clears the dirty flag. The caller is responsible for not calling it
 *     every tick; use it only where the client must have the value immediately (login, respawn,
 *     dimension change, an explicit admin command).</li>
 *     <li>{@link #syncIfDirty(EntityPlayerMP)} is safe to call often — it backs off on its own
 *     when there is nothing to send. Use it for periodic callers (a tick handler) and for any
 *     path that may fire many times per second.</li>
 * </ul>
 *
 * <p>Max mana is deliberately never sent over this channel: it rides Forge's attribute sync
 * instead, because the {@code MAX_MANA} attribute has {@code setShouldWatch(true)}.
 */
public final class ManaNetwork {

    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(ManaCoreMod.MODID);

    private ManaNetwork() {
    }

    public static void register() {
        // Discriminator 0 belongs to PacketManaSync. The next packet added to this channel
        // must be registered as 1 — ids must match on both ends or the channel desyncs.
        CHANNEL.registerMessage(PacketManaSync.Handler.class, PacketManaSync.class, 0, Side.CLIENT);
    }

    /**
     * Sends the current mana value unconditionally and clears the dirty flag. The caller is
     * responsible for not invoking this every tick — prefer {@link #syncIfDirty(EntityPlayerMP)}
     * for periodic or possibly-repeated call sites.
     */
    public static void syncNow(EntityPlayerMP player) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            ManaCoreMod.LOGGER.debug("[ManaCore] syncNow: no IManaPool capability for {}", player);
            return;
        }
        CHANNEL.sendTo(new PacketManaSync(pool.getCurrent()), player);
        pool.setDirty(false);
    }

    /**
     * Sends the current mana value only when {@link IManaPool#isDirty()} is true. Safe to call
     * often — it is a no-op when there is nothing new to send.
     */
    public static void syncIfDirty(EntityPlayerMP player) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            ManaCoreMod.LOGGER.debug("[ManaCore] syncIfDirty: no IManaPool capability for {}", player);
            return;
        }
        if (!pool.isDirty()) {
            return;
        }
        CHANNEL.sendTo(new PacketManaSync(pool.getCurrent()), player);
        pool.setDirty(false);
    }
}
