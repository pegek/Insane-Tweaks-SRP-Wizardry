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
 *     <li>{@link #syncIfDirty(EntityPlayerMP)} coalesces changes and skips the send when there
 *     is nothing new, but it carries no rate limit of its own — see its javadoc for what that
 *     does and does not buy the caller.</li>
 * </ul>
 *
 * <p>Max mana is deliberately never sent over this channel: it rides Forge's attribute sync
 * instead, because the {@code MAX_MANA} attribute has {@code setShouldWatch(true)}.
 *
 * <p>This class deliberately has no internal time gate of its own (no "only send every N ticks"
 * built into {@code syncIfDirty}). Adding one would create a third sending strategy alongside
 * {@code syncNow} and {@code syncIfDirty} instead of simplifying either of them, and it would
 * need a per-player "last synced at" state that this class does not have and should not grow —
 * that bookkeeping belongs to whichever periodic caller decides the interval, not to the channel
 * itself. In practice that caller is a tick handler that already gates on {@code ticksExisted % N},
 * the standard pattern elsewhere in this repo. High-frequency paths (e.g. the upkeep cost of a
 * channelled spell) are not meant to call into this class's sync methods at all: they should
 * subtract mana and leave the pool marked {@code dirty}, and let the periodic
 * {@link #syncIfDirty(EntityPlayerMP)} call pick the value up on its next pass.
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
        IManaPool pool = requirePool(player, "syncNow");
        if (pool != null) {
            send(player, pool);
        }
    }

    /**
     * Sends the current mana value only when {@link IManaPool#isDirty()} is true, clearing the
     * flag afterward. This coalesces multiple changes into a single packet when called
     * periodically — it does not, by itself, limit how often packets go out.
     *
     * <p>It carries no rate limit of its own: if the value changes every tick (regen, the upkeep
     * cost of a channelled spell), the pool is dirty on almost every tick, so calling this every
     * tick sends a packet every tick. Keeping the call frequency in check — e.g. gating on
     * {@code ticksExisted % N} in a tick handler — is the caller's responsibility.
     */
    public static void syncIfDirty(EntityPlayerMP player) {
        IManaPool pool = requirePool(player, "syncIfDirty");
        if (pool != null && pool.isDirty()) {
            send(player, pool);
        }
    }

    /**
     * Looks up the player's mana pool capability, logging (with the calling method's name as a
     * prefix, so the two send paths stay distinguishable in the log) and returning {@code null}
     * when it is missing. Shared by {@link #syncNow(EntityPlayerMP)} and
     * {@link #syncIfDirty(EntityPlayerMP)} so the missing-capability log can't drift between them.
     */
    private static IManaPool requirePool(EntityPlayerMP player, String logPrefix) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            ManaCoreMod.LOGGER.debug("[ManaCore] {}: no IManaPool capability for {}", logPrefix, player);
        }
        return pool;
    }

    /**
     * Shared send path: writes the packet for the given pool's current value and clears the
     * dirty flag. Centralized so a change to packet construction can't be made for one send path
     * and forgotten for the other.
     */
    private static void send(EntityPlayerMP player, IManaPool pool) {
        CHANNEL.sendTo(new PacketManaSync(pool.getCurrent()), player);
        pool.setDirty(false);
    }
}
