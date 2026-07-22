package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Vetoes parasite terrain destruction inside an active sanctuary. SRP's
 * {@code EntityParasiteBase.skillBreakBlocks()} fires the cancelable Forge
 * {@link LivingDestroyBlockEvent} per block, so a plain handler suffices (no mixin).
 * Does NOT cover {@code AIDisableBeaconIki} (bypasses the event) — tracked as a follow-up.
 */
public class SanctuaryBlockBreakVetoHandler {

    @SubscribeEvent
    public void onDestroyBlock(LivingDestroyBlockEvent event) {
        if (!ModConfig.sanctuary.vetoBlockBreak) {
            return;
        }
        EntityLivingBase e = event.getEntityLiving();
        if (e == null || e.world.isRemote) {
            return;
        }
        if (!SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (SanctuaryRegionHelper.isProtected(e.world, pos)) {
            event.setCanceled(true);
            SanctuaryDebug.log(e.world.getTotalWorldTime(), "grief-vetoed",
                    e.getName() + " break @(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
        }
    }
}
