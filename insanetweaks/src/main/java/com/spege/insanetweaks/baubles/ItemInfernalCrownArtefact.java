package com.spege.insanetweaks.baubles;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModItems;

import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ItemInfernalCrownArtefact extends ItemArtefact {

    private static final String SUMMON_COUNT_TAG = "InsaneTweaksSummonCount";
    private static final String PROCESSED_SUMMON_TAG = "InsaneTweaksInfernalCrownProcessed";
    private static final int INFERNAL_THRESHOLD = 10;

    public ItemInfernalCrownArtefact() {
        super(EnumRarity.EPIC, Type.HEAD);
        this.setRegistryName("infernal_crown");
        this.setUnlocalizedName("insanetweaks.infernal_crown");
    }

    /**
     * Runs after InfernalMobs has processed the spawn so we can:
     * 1. see natural infernal rolls correctly
     * 2. force the 5th summon through the direct API when needed
     * 3. avoid double-counting the same summon on later world re-joins
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) return;
        if (!(event.getEntity() instanceof EntityLivingBase)) return;
        if (!(event.getEntity() instanceof ISummonedCreature)) return;

        EntityLivingBase minion = (EntityLivingBase) event.getEntity();
        if (minion instanceof EntitySnowman) return;
        if (minion instanceof EntityVillager) return;

        NBTTagCompound minionData = minion.getEntityData();
        if (minionData.getBoolean(PROCESSED_SUMMON_TAG)) return;
        minionData.setBoolean(PROCESSED_SUMMON_TAG, true);

        EntityPlayer player = getPlayerCaster((ISummonedCreature) event.getEntity());
        if (player == null) return;

        if (!ItemArtefact.isArtefactActive(player, ModItems.INFERNAL_CROWN)) return;

        // Guard: InfernalMobs must be loaded — without it we cannot do anything useful.
        if (!Loader.isModLoaded("infernalmobs")) return;

        NBTTagCompound persisted = getOrCreatePersistedData(player);

        if (com.spege.insanetweaks.util.InfernalMobsCompat.isRare(minion)) {
            persisted.setInteger(SUMMON_COUNT_TAG, 0);
            return;
        }

        int count = persisted.getInteger(SUMMON_COUNT_TAG) + 1;
        if (count >= INFERNAL_THRESHOLD) {
            boolean success = com.spege.insanetweaks.util.InfernalMobsCompat.forceInfernal(minion);
            if (success || com.spege.insanetweaks.util.InfernalMobsCompat.isRare(minion)) {
                count = 0;
            } else {
                // Keep the next summon eligible if the direct InfernalMobs call fails.
                count = INFERNAL_THRESHOLD - 1;
                InsaneTweaksMod.LOGGER.warn(
                        "[InsaneTweaks] Infernal Crown could not mark summon {} as infernal for player {}",
                        minion.getName(), player.getName());
            }
        }

        persisted.setInteger(SUMMON_COUNT_TAG, count);
    }

    private static EntityPlayer getPlayerCaster(ISummonedCreature summonedCreature) {
        EntityLivingBase caster = summonedCreature.getCaster();
        return caster instanceof EntityPlayer ? (EntityPlayer) caster : null;
    }

    private static NBTTagCompound getOrCreatePersistedData(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

}
