package com.spege.insanetweaks.events;

import java.util.Iterator;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.sapling.SaplingDropPenalty;
import com.spege.insanetweaks.entities.sapling.SaplingTags;
import com.spege.insanetweaks.init.ModItems;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What happens to parasites drawn into a Corrupted Sapling defense ({@link SaplingTags#BOUND}):
 *
 * <ul>
 *   <li><b>Provocation</b> — a player hitting one makes it turn on that player: SRP's target lock
 *   ({@code targetNewCool}) is cleared, the player becomes the locked target, and the lure leaves
 *   it alone until the provocation runs out (refreshed by every hit).</li>
 *   <li><b>Drop penalty</b> — every dropped stack is lost with {@code saplingDropPenalty} and the
 *   experience is scaled down, so the event is not a farm. Corrupted Seed Fragments are penalised
 *   in {@link CorruptedFragmentDropHandler} instead, where the pity counter can see the outcome.</li>
 * </ul>
 *
 * Names {@link EntityParasiteBase} directly, so it is registered only when SRParasites is loaded.
 */
public class SaplingParasiteHandler {

    @SubscribeEvent
    public void onParasiteAttacked(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || !(victim instanceof EntityParasiteBase) || !SaplingTags.isBound(victim)) {
            return;
        }
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        int ticks = ModConfig.baubleFruits.saplingProvokeSeconds * 20;
        if (ticks <= 0) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();
        EntityParasiteBase parasite = (EntityParasiteBase) victim;
        parasite.targetNewCool = 0; // otherwise the sapling lock rejects the new target
        parasite.setAttackTarget(player, ticks);
        SaplingTags.markProvoked(parasite, victim.world.getTotalWorldTime() + ticks);
    }

    /** LOWEST, so it also thins out drops other handlers added before it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBoundDrops(LivingDropsEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        double penalty = ModConfig.baubleFruits.saplingDropPenalty;
        if (victim.world.isRemote || penalty <= 0.0D || !SaplingTags.isBound(victim)) {
            return;
        }
        Iterator<EntityItem> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            EntityItem drop = drops.next();
            if (drop.getItem().getItem() == ModItems.CORRUPTED_SEED_FRAGMENT) {
                continue;
            }
            if (!SaplingDropPenalty.keepsDrop(victim.world.rand.nextDouble(), penalty)) {
                drops.remove();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBoundExperience(LivingExperienceDropEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        double penalty = ModConfig.baubleFruits.saplingDropPenalty;
        if (victim.world.isRemote || penalty <= 0.0D || !SaplingTags.isBound(victim)) {
            return;
        }
        event.setDroppedExperience(SaplingDropPenalty.reducedXp(event.getDroppedExperience(), penalty,
                victim.world.rand.nextDouble()));
    }
}
