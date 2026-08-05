package com.spege.srpwizcore.whtcompat;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Observes attack outcomes and dumps the counters on an interval.
 *
 * <p>The listener sits at {@link EventPriority#LOWEST} on {@code LivingAttackEvent}, which is
 * after WorseHurtTimer's own handler, so {@code isCanceled()} tells us whether WHT refused the
 * attack. That is the one fact none of our mixins can see — they run while the decision is still
 * being computed — and getting it this way needs no mixin at all.
 *
 * <p>{@code receiveCanceled} must stay true: a cancelled attack is exactly the case we are
 * counting, and without it the interesting half of the data never arrives.
 */
public class WhtDiagHandler {

    private int tickCounter = 0;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!WhtDiag.ENABLED) {
            return;
        }
        if (event.getEntityLiving() == null || event.getEntityLiving().world.isRemote) {
            return;
        }
        WhtDiag.recordAttack(event.getEntityLiving(), event.getSource().getTrueSource(),
                event.getSource().getDamageType(), event.isCanceled());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER) {
            return;
        }
        if (!WhtDiag.ENABLED) {
            return;
        }
        int every = SrpWizCoreConfig.whtCompat.diagDumpIntervalSeconds;
        if (every <= 0) {
            return;
        }
        if (++this.tickCounter < every * 20) {
            return;
        }
        this.tickCounter = 0;
        WhtDiag.dumpToLog();
    }
}
