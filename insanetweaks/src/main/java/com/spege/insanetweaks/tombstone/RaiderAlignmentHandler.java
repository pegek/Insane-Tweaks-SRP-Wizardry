package com.spege.insanetweaks.tombstone;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.TombstoneCategory.RaiderAlignmentConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ovh.corail.tombstone.api.capability.ITBCapability;
import ovh.corail.tombstone.capability.TBCapabilityProvider;
import ovh.corail.tombstone.config.ConfigTombstone;
import ovh.corail.tombstone.registry.ModTriggers;

/**
 * Bridges a third-party raid mod into Tombstone's alignment economy.
 *
 * <p>Tombstone ships its own village siege and recognises a raider as exactly one thing: an
 * {@code EntityZombie} carrying the NBT flag {@code siege=true}, set by its own siege handler
 * ({@code EntityHelper.applyKillResult}). A pack that runs a different raid system therefore
 * gets no alignment and no {@code KILL_ENOUGH_RAIDER} advancement for killing actual raiders,
 * and the two siege systems end up competing over the same villages.
 *
 * <p>This handler closes that gap from the other side: kill an entity on the configured list and
 * it pays out exactly what Tombstone pays for its own raiders — {@code alignment.pointsKillRaider}
 * from {@code tombstone.cfg}, which stays the single place to tune the reward. Once it is live,
 * Tombstone's own {@code village_siege.handleVillageSiege} can be turned off without losing the
 * points it used to be the only source of.
 */
public class RaiderAlignmentHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world == null || victim.world.isRemote) {
            return;
        }

        RaiderAlignmentConfig cfg = ModConfig.tombstone.raiderAlignment;
        if (!ModConfig.tombstone.enableTombstoneTweaks || !cfg.enabled) {
            return;
        }

        Entity source = event.getSource() == null ? null : event.getSource().getTrueSource();
        if (!(source instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) source;

        ResourceLocation key = EntityList.getKey(victim);
        if (key == null || !isRaider(key.toString(), cfg)) {
            return;
        }

        int points = ConfigTombstone.alignment.pointsKillRaider;

        if (cfg.debugLogging) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Raider kill: {} killed {} for {} alignment.",
                    player.getName(), key, Integer.valueOf(points));
        }

        // reward() is a no-op at 0 and handles the client sync, the alignment-level change and the
        // double-payout when the points pull you back across the neutral line. Nothing to reimplement.
        ITBCapability cap = player.getCapability(TBCapabilityProvider.TB_CAPABILITY, null);
        if (cap != null) {
            cap.reward(player, 0, points);
        }

        // Fired even when the points are 0 — the advancement counts kills, not alignment.
        ModTriggers.KILL_ENOUGH_RAIDER.trigger(player);
    }

    /**
     * Exact registry-name match, deliberately not a prefix one: {@code raids:} would sweep in that
     * mod's non-raider entities, and the whole point of the default list is that it is narrow.
     */
    private static boolean isRaider(String registryName, RaiderAlignmentConfig cfg) {
        if (cfg.raiderEntityTypes == null) {
            return false;
        }
        for (String entry : cfg.raiderEntityTypes) {
            if (entry != null && registryName.equals(entry.trim())) {
                return true;
            }
        }
        return false;
    }
}
