package com.spege.tombtweaks.events;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.FirstKillRewardConfig;
import com.spege.tombtweaks.util.FirstKillRewardRegistry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import ovh.corail.tombstone.api.capability.ITBCapability;
import ovh.corail.tombstone.capability.TBCapabilityProvider;

/**
 * Pays a one-off knowledge reward the first time a player kills a configured enemy.
 *
 * <p>Tombstone has no reward for killing anything dangerous — alignment covers seven unrelated
 * deeds and knowledge arrives only through advancements and loot — so a boss is worth nothing to
 * the Knowledge of Death tree. This fills that gap without turning into a grind, because each
 * enemy pays exactly once per player, forever.
 *
 * <h3>Why the config is in perk points</h3>
 * Points are {@code floor(sqrt(knowledge - 1))} and Tombstone's own inverse is
 * {@code getKnowledgeForLevel(p) = p * p + 1}. So the knowledge one point costs grows without
 * limit, and a fixed knowledge grant would be a level early on and a rounding error later. The
 * reward is therefore declared in points and converted here, against the player's current total:
 *
 * <pre>knowledge to add = (p + n)² − p² = n × (2p + n)</pre>
 *
 * That lands on exactly {@code p + n} points and never overshoots — the player's leftover progress
 * towards their next point is carried, not discarded, and it cannot round up into a free extra
 * point (their knowledge is below {@code (p+1)² + 1}, and adding the gap keeps it below
 * {@code (p+n+1)² + 1}).
 *
 * <h3>Why the claim list lives in PlayerPersisted</h3>
 * {@code EntityPlayer.PERSISTED_NBT_TAG} is the one part of a player's entity data that survives
 * death and respawn. Anywhere else, dying to the boss you just killed would refund the reward.
 */
public class FirstKillRewardHandler {

    /** Inside {@code PlayerPersisted}. New key, so it takes this mod's own name. */
    private static final String CLAIMED_KEY = "tombtweaks_first_kills";

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world == null || victim.world.isRemote) {
            return;
        }

        FirstKillRewardConfig cfg = TombTweaksConfig.tombstone.firstKillRewards;
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !cfg.enabled) {
            return;
        }
        if (FirstKillRewardRegistry.isEmpty()) {
            return;
        }

        Entity source = event.getSource() == null ? null : event.getSource().getTrueSource();
        if (!(source instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) source;

        ResourceLocation key = EntityList.getKey(victim);
        if (key == null) {
            return;
        }
        String name = key.toString();

        int points = FirstKillRewardRegistry.pointsFor(name);
        if (points <= 0) {
            return;
        }

        if (hasClaimed(player, name)) {
            if (cfg.debugLogging) {
                TombstoneTweaks.LOGGER.info("[TombstoneTweaks] First-kill reward: {} killed {} again — already claimed, paid nothing.",
                        player.getName(), name);
            }
            return;
        }

        ITBCapability cap = player.getCapability(TBCapabilityProvider.TB_CAPABILITY, null);
        if (cap == null) {
            return;
        }

        int before = cap.getTotalPerkPoints();
        int knowledge = points * (2 * before + points);
        cap.reward(player, knowledge, 0);
        markClaimed(player, name);

        if (cfg.debugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] First-kill reward: {} killed {} — {} perk points, {} knowledge, {} -> {}.",
                    player.getName(), name, Integer.valueOf(points), Integer.valueOf(knowledge),
                    Integer.valueOf(before), Integer.valueOf(cap.getTotalPerkPoints()));
        }

        if (cfg.announceInChat) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.LIGHT_PURPLE + "A first kill: " + victim.getName()
                    + TextFormatting.RESET + " — the dead grant you "
                    + TextFormatting.AQUA + points + (points == 1 ? " perk point" : " perk points")
                    + TextFormatting.RESET + "."));
        }
    }

    private static boolean hasClaimed(EntityPlayer player, String name) {
        NBTTagList claimed = player.getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getTagList(CLAIMED_KEY, Constants.NBT.TAG_STRING);
        for (int i = 0; i < claimed.tagCount(); i++) {
            if (name.equals(claimed.getStringTagAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void markClaimed(EntityPlayer player, String name) {
        NBTTagCompound data = player.getEntityData();
        // getCompoundTag hands back a fresh compound when the key is absent, so the result has to
        // be written back or the very first claim on a new player would be lost.
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagList claimed = persisted.getTagList(CLAIMED_KEY, Constants.NBT.TAG_STRING);
        claimed.appendTag(new NBTTagString(name));
        persisted.setTag(CLAIMED_KEY, claimed);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }
}
