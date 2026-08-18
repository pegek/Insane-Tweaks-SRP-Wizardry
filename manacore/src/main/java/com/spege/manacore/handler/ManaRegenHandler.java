package com.spege.manacore.handler;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.ManaMath;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaRegenHandler {

    /**
     * How often a sync packet is allowed to go out. This gate lives here, not in
     * {@link ManaNetwork}: {@link ManaNetwork#syncIfDirty(EntityPlayerMP)} deliberately carries
     * no rate limit of its own (see its javadoc), and regen dirties the pool on essentially every
     * tick, so calling it unconditionally from this handler would mean one packet per player per
     * tick. Gating on {@code ticksExisted % SYNC_INTERVAL_TICKS} here is what turns that into one
     * packet per player per interval instead - log and network traffic are not free on the server
     * thread.
     */
    private static final int SYNC_INTERVAL_TICKS = 10;

    private ManaRegenHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }

        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }

        double perTick = ManaMath.regenPerTick(
                ManaCoreConfig.regen.amountPerCycle, ManaCoreConfig.regen.cycleSeconds);
        if (perTick > 0.0D) {
            double max = ManaAttributes.getMaxMana(player);
            pool.setCurrent(ManaMath.afterRegen(pool.getCurrent(), max, perTick));
        }

        if (player.ticksExisted % SYNC_INTERVAL_TICKS == 0) {
            ManaNetwork.syncIfDirty((EntityPlayerMP) player);
        }
    }
}
