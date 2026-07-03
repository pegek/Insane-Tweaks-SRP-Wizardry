package com.spege.insanetweaks.util;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfig;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper.OriginalSnapshot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Bridge for wizard assimilation.
 *
 * EB Wizardry wizards now map to our local sim_wizard implementation, which is a
 * full-fledged SRP parasite (not a protected summon). EPEL_E is intentionally NOT
 * applied here - sim_wizard is meant to be aggressive against everything non-parasite
 * and to participate in the SRP collective normally.
 *
 * Ancient Spellcraft class wizards still use the temporary sim_human fallback
 * until sim_battlemage exists. The sim_wizard target itself can be disabled at
 * runtime via {@link com.spege.insanetweaks.config.ModConfig.SimWizard#enabled};
 * in that case the bridge falls back to sim_human for both EBW wizard variants.
 */
public final class SrpWizardryAssimilationHelper {

    private static final ResourceLocation WIZARD_ID = new ResourceLocation("ebwizardry", "wizard");
    private static final ResourceLocation EVIL_WIZARD_ID = new ResourceLocation("ebwizardry", "evil_wizard");
    private static final ResourceLocation CLASS_WIZARD_ID = new ResourceLocation("ancientspellcraft", "class_wizard");
    private static final ResourceLocation EVIL_CLASS_WIZARD_ID = new ResourceLocation("ancientspellcraft", "evil_class_wizard");
    private static final ResourceLocation SIM_WIZARD_ID = new ResourceLocation(InsaneTweaksMod.MODID, "sim_wizard");
    private static final ResourceLocation TEST_SIM_HUMAN_ID = new ResourceLocation("srparasites", "sim_human");

    private SrpWizardryAssimilationHelper() {
    }

    public static boolean tryConvertSupportedWizard(EntityLivingBase original, NBTTagCompound tags) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || original == null) {
            return false;
        }

        World world = original.world;
        if (world == null || world.isRemote) {
            return false;
        }

        ResourceLocation originalId = EntityList.getKey(original);
        ResourceLocation targetId = resolveConversionTarget(originalId);
        if (targetId == null) {
            return false;
        }

        Entity convertedEntity = EntityList.createEntityByIDFromName(targetId, world);
        if (!(convertedEntity instanceof EntityLiving)) {
            InsaneTweaksMod.LOGGER.warn(
                "[IT][AssimilationBridge] Failed to create '{}' for '{}'",
                targetId, originalId);
            return false;
        }

        EntityLiving converted = (EntityLiving) convertedEntity;
        OriginalSnapshot snapshot = captureSnapshot(original, originalId);

        converted.setLocationAndAngles(
            original.posX, original.posY, original.posZ,
            original.rotationYaw, original.rotationPitch);
        converted.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(converted)), null);

        if (original.hasCustomName()) {
            converted.setCustomNameTag(original.getCustomNameTag());
            converted.setAlwaysRenderNameTag(original.getAlwaysRenderNameTag());
        }

        if (snapshot != null) {
            applySnapshot(converted, snapshot);
        }

        if (converted instanceof EntityParasiteBase) {
            EntityParasiteBase parasite = (EntityParasiteBase) converted;
            SRPSaveData.get(world, 104).addNumberIDDataSpawn(parasite.getParasiteIDRegister());
            parasite.cannotDespawn(SRPConfig.convertedDespawn);
            // v2: EPEL_E intentionally NOT applied. sim_wizard is a full SRP parasite, not
            // a protected summon - it participates in COTH/spawn lifecycle like any other
            // EntityInfHuman-derived parasite. EPEL_E is reserved for ally summons such as
            // EntityFerCowMinion (handled via SummonInfectionSafetyHelper).
        }

        if (converted instanceof EntityPInfected) {
            // Empty host disables the native infected de-hide path that would
            // otherwise try to rebuild a non-EntityMob host such as wizard.
            ((EntityPInfected) converted).setHost("");
        }

        world.removeEntity(original);
        world.spawnEntity(converted);
        world.playEvent(null, 1026, new BlockPos(converted), 0);

        if (converted instanceof EntityParasiteBase) {
            ((EntityParasiteBase) converted).particleStatus((byte) 7);
        }

        InsaneTweaksMod.LOGGER.info(
            "[IT][AssimilationBridge] convert '{}' -> '{}'",
            originalId, targetId);
        return true;
    }

    private static ResourceLocation resolveConversionTarget(ResourceLocation originalId) {
        if (WIZARD_ID.equals(originalId) || EVIL_WIZARD_ID.equals(originalId)) {
            // ModConfig.simWizard.enabled is a master switch. When disabled, fall back to
            // sim_human so the bridge keeps working without spawning the custom entity.
            return ModConfig.simWizard.enabled ? SIM_WIZARD_ID : TEST_SIM_HUMAN_ID;
        }

        if (CLASS_WIZARD_ID.equals(originalId) || EVIL_CLASS_WIZARD_ID.equals(originalId)) {
            return TEST_SIM_HUMAN_ID;
        }

        return null;
    }

    private static OriginalSnapshot captureSnapshot(EntityLivingBase entity, ResourceLocation originalId) {
        try {
            NBTTagCompound nbt = new NBTTagCompound();
            entity.writeToNBT(nbt);
            nbt.removeTag("DeathTime");
            nbt.removeTag("HurtTime");
            nbt.removeTag("FallDistance");
            nbt.removeTag("srpcothimmunity");
            nbt.setFloat("Health", entity.getMaxHealth() * 0.75f);
            cleanSrpEffects(nbt);
            return new OriginalSnapshot(originalId.toString(), nbt, System.currentTimeMillis());
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn(
                "[IT][AssimilationBridge] Failed to capture snapshot for '{}': {}",
                originalId, ex.getMessage());
            return null;
        }
    }

    private static void applySnapshot(Entity target, OriginalSnapshot snapshot) {
        NBTTagCompound data = target.getEntityData();
        data.setString(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID, snapshot.resourceId);
        data.setTag(SrpOriginSnapshotHelper.KEY_ORIGINAL_NBT, snapshot.fullNbt.copy());
    }

    private static void cleanSrpEffects(NBTTagCompound nbt) {
        if (!nbt.hasKey("ActiveEffects", 9)) {
            return;
        }

        NBTTagList effectList = nbt.getTagList("ActiveEffects", 10);
        NBTTagList cleaned = new NBTTagList();
        for (int i = 0; i < effectList.tagCount(); i++) {
            NBTTagCompound effect = effectList.getCompoundTagAt(i);
            int potionId = effect.getByte("Id") & 0xFF;
            if (potionId < 100) {
                cleaned.appendTag(effect);
            }
        }
        nbt.setTag("ActiveEffects", cleaned);
    }
}
