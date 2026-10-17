package com.spege.srpwizcore.devspeed;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Event side of the dev-only game-speed tool. Registered only when {@code devSpeed} is enabled.
 *
 * <p>Every type here is common (the {@code FMLNetworkEvent} client events live in
 * {@code fml.common.network} and load fine on a dedicated server; they just never fire there).
 */
public class DevSpeedHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            net.minecraft.server.MinecraftServer server =
                    FMLCommonHandler.instance().getMinecraftServerInstance();
            DevSpeed.onServerTick(server);
            // After the sample: the ring records the clock as vanilla left it this tick.
            DevSpeedWarp.onServerTick(server);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            DevSpeed.syncTo((EntityPlayerMP) event.player);
        }
    }

    /**
     * Overworld only: other dimensions unload whenever they empty out, and resetting then would
     * drop the speed in the middle of a test. Dimension 0 unloads only when the world closes.
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote && event.getWorld().provider.getDimension() == 0) {
            DevSpeedWarp.stop(null, "overworld unload");
            DevSpeed.reset(null, "overworld unload");
        }
    }

    /** Client: leaving a server must never carry its speed into the next one. */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        DevSpeedState.applyClient(DevSpeedState.VANILLA_MS_PER_TICK);
    }
}
