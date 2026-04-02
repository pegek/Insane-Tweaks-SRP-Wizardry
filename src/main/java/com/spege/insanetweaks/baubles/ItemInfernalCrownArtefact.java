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
    private static final int INFERNAL_THRESHOLD = 5;

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

        NBTTagCompound persisted = getOrCreatePersistedData(player);
        boolean hasInfernalMobs = Loader.isModLoaded("infernalmobs");

        if (!hasInfernalMobs) {
            persisted.setInteger(SUMMON_COUNT_TAG, 0);
            return;
        }

        if (InfernalMobsDirectAPI.isRare(minion)) {
            persisted.setInteger(SUMMON_COUNT_TAG, 0);
            return;
        }

        int count = persisted.getInteger(SUMMON_COUNT_TAG) + 1;
        if (count >= INFERNAL_THRESHOLD) {
            boolean success = InfernalMobsDirectAPI.forceInfernal(minion);
            if (success || InfernalMobsDirectAPI.isRare(minion)) {
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

    /**
     * Direct InfernalMobs bridge used by the crown. This bypasses the normal
     * IEntityOwnable spawn restriction by calling addEntityModifiersByString().
     */
    public static class InfernalMobsDirectAPI {

        private static java.lang.reflect.Method instanceMethod;
        private static java.lang.reflect.Method addModifiersMethod;
        private static java.lang.reflect.Method isRareMethod;

        public static boolean forceInfernal(EntityLivingBase entity) {
            try {
                Object core = getCore();
                if (core == null) return false;

                if (isRare(entity)) {
                    return true;
                }

                if (addModifiersMethod == null) {
                    addModifiersMethod = core.getClass().getDeclaredMethod(
                            "addEntityModifiersByString", EntityLivingBase.class, String.class);
                    addModifiersMethod.setAccessible(true);
                }

                addModifiersMethod.invoke(core, entity, "Regen Sprint");
                return isRare(entity);

            } catch (Exception e) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Infernal Crown direct API call failed", e);
                return false;
            }
        }

        public static boolean isRare(EntityLivingBase entity) {
            try {
                if (isRareMethod == null) {
                    Class<?> clazz = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
                    isRareMethod = clazz.getDeclaredMethod("getIsRareEntity", EntityLivingBase.class);
                    isRareMethod.setAccessible(true);
                }
                Object result = isRareMethod.invoke(null, entity);
                return result instanceof Boolean && (Boolean) result;
            } catch (Exception e) {
                return false;
            }
        }

        private static Object getCore() throws Exception {
            if (instanceMethod == null) {
                Class<?> clazz = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
                instanceMethod = clazz.getDeclaredMethod("instance");
                instanceMethod.setAccessible(true);
            }
            return instanceMethod.invoke(null);
        }
    }
}
