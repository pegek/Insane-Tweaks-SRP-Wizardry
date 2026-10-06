package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.sapling.SaplingDropPenalty;
import com.spege.insanetweaks.entities.sapling.SaplingTags;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;
import com.spege.insanetweaks.util.FragmentPity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Corrupted Seed Fragment drops from high-tier parasites (configurable prefix list),
 * ONLY when the killing player wears a qualifying ring (Blessed Ring or the plain
 * Cursed Ring — full parity per spec 2026-07-10). Registered when both
 * enableBaubleFruits and SRP are present.
 *
 * <p>Since 1.21.0 the roll has bad-luck protection ({@link FragmentPity}): eligible kills without a
 * drop are counted in persisted player NBT and the {@code fragmentPityKills}-th one always drops.
 */
public class CorruptedFragmentDropHandler {

    private static final String HINT_SHOWN_TAG = "InsaneTweaksCorruptedHintShown";
    private static final String PITY_TAG = "InsaneTweaksFragmentPity";

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        Entity killed = event.getEntity();
        if (killed.world.isRemote) return;
        if (!(killed instanceof EntityParasiteBase)) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer killer = (EntityPlayer) event.getSource().getTrueSource();
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(killer)) return;

        ResourceLocation key = EntityList.getKey(killed);
        if (key == null || !isHighTier(key.toString())) return;

        sendOneTimeHint(killer);

        NBTTagCompound persisted = persistedData(killer);
        int dryKills = persisted.getInteger(PITY_TAG);
        boolean drop = FragmentPity.drops(killed.world.rand.nextDouble(),
                ModConfig.baubleFruits.fragmentDropChance, dryKills, ModConfig.baubleFruits.fragmentPityKills);
        // Sapling-defense drop penalty: a lost fragment counts as a dry kill, so pity still catches it.
        if (drop && SaplingTags.isBound(killed) && !SaplingDropPenalty.keepsDrop(
                killed.world.rand.nextDouble(), ModConfig.baubleFruits.saplingDropPenalty)) {
            drop = false;
        }
        persisted.setInteger(PITY_TAG, FragmentPity.nextDryKills(dryKills, drop));
        if (!drop) return;

        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(ModItems.CORRUPTED_SEED_FRAGMENT)));
    }

    private static boolean isHighTier(String registryName) {
        for (String prefix : ModConfig.baubleFruits.fragmentDropEntities) {
            if (prefix != null && !prefix.isEmpty() && registryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The player's persisted (death-surviving) Forge data compound, created on first use. */
    private static NBTTagCompound persistedData(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** One-time flavor hint (per player, persists through death) the first time a
     *  qualifying-ring wearer kills a fragment-eligible parasite. */
    private static void sendOneTimeHint(EntityPlayer player) {
        NBTTagCompound persisted = persistedData(player);
        if (persisted.getBoolean(HINT_SHOWN_TAG)) return;
        persisted.setBoolean(HINT_SHOWN_TAG, true);
        player.sendMessage(new TextComponentTranslation("msg.insanetweaks.corrupted_hint"));
    }
}
