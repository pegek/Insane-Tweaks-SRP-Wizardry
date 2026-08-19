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

        double max = ManaAttributes.getMaxMana(player);

        // Enforced BEFORE the regen guard and independently of it, because this is the only thing
        // that takes back a surplus, and a surplus can appear while regen is switched off: the
        // maximum drops the moment a bauble granting bonus maximum comes off, leaving the pool
        // above a ceiling nothing else would ever pull it back under. Folding this into the
        // `perTick > 0` branch would make "regen disabled" quietly mean "surplus kept forever".
        //
        // Both attribute reads behind getMaxMana are cached by the attribute instance and
        // recomputed only when a modifier changes, so this costs nothing per tick.
        if (pool.getCurrent() > max) {
            pool.setCurrent(max);
        }

        double perTick = ManaMath.regenPerTick(
                ManaCoreConfig.regen.amountPerCycle, ManaCoreConfig.regen.cycleSeconds);
        if (perTick > 0.0D) {
            pool.setCurrent(ManaMath.afterRegen(pool.getCurrent(), max, perTick));
        }

        // Deliberately outside the regen guard above: this is the only periodic flush of dirty
        // pool state to the client, so it must run even when regen is switched off. Writers that
        // change the pool without syncing themselves - notably the per-tick upkeep of a channelled
        // spell, which subtracts mana and leaves the pool dirty rather than sending a packet every
        // tick - depend on this call to deliver their value. An "optimisation" that returns early
        // when regen is disabled would silently stop those updates from ever reaching the HUD.
        if (player.ticksExisted % SYNC_INTERVAL_TICKS == 0) {
            ManaNetwork.syncIfDirty((EntityPlayerMP) player);
        }
    }
}
