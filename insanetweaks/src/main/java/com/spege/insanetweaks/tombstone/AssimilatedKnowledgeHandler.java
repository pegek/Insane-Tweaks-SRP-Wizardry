package com.spege.insanetweaks.tombstone;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.TombstoneCategory.AssimilatedKnowledgeConfig;
import com.spege.insanetweaks.tombstone.perks.PerkAssimilatedKnowledge;
import com.spege.insanetweaks.util.TombstonePerkHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ovh.corail.tombstone.api.capability.ITBCapability;
import ovh.corail.tombstone.capability.TBCapabilityProvider;

/**
 * Runtime for the <b>Assimilated Knowledge</b> perk: a qualifying parasite kill has a
 * per-level chance to grant Tombstone knowledge.
 *
 * <p>Tombstone never invokes a perk, so the effect has to live in a handler like this one that
 * asks the capability for the player's level and acts on it.
 *
 * <p>The entity filter is not decoration. Perk points are {@code floor(sqrt(knowledge - 1))},
 * so knowledge 101 buys only 10 points — if every buglin counted, a single night of farming
 * would max the whole tree. Only the tiers listed in config qualify.
 */
public class AssimilatedKnowledgeHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world == null || victim.world.isRemote) {
            return;
        }

        AssimilatedKnowledgeConfig cfg = ModConfig.tombstone.assimilatedKnowledge;
        if (!ModConfig.tombstone.enableTombstoneTweaks || !cfg.enabled || cfg.maxLevel <= 0) {
            return;
        }

        Entity source = event.getSource() == null ? null : event.getSource().getTrueSource();
        if (!(source instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) source;

        ResourceLocation key = EntityList.getKey(victim);
        if (key == null || !qualifies(key.toString(), cfg)) {
            return;
        }

        int level = TombstonePerkHelper.getPerkLevel(player, TombstonePerkHelper.PERK_ASSIMILATED_KNOWLEDGE);
        if (level <= 0) {
            return;
        }

        double chance = PerkAssimilatedKnowledge.chanceAtLevel(level);
        boolean granted = victim.world.rand.nextDouble() < chance;

        if (cfg.debugLogging) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Assimilated Knowledge: {} killed {} at perk level {} (chance {}) -> {}",
                    player.getName(), key, Integer.valueOf(level), Double.valueOf(chance),
                    granted ? "granted" : "no proc");
        }

        if (!granted) {
            return;
        }

        ITBCapability cap = player.getCapability(TBCapabilityProvider.TB_CAPABILITY, null);
        if (cap == null) {
            return;
        }
        cap.reward(player, cfg.knowledgePerProc, cfg.alignmentPerProc);
    }

    /** Prefix match against the configured tier list — an exact name is its own prefix. */
    private static boolean qualifies(String registryName, AssimilatedKnowledgeConfig cfg) {
        if (cfg.qualifyingEntityPrefixes == null) {
            return false;
        }
        for (String prefix : cfg.qualifyingEntityPrefixes) {
            if (prefix != null && !prefix.isEmpty() && registryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
