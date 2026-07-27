package com.spege.insanetweaks.util;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPInfected;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfig;
import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.entities.SimWizardTier;
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
 * <p>Which source entity becomes what is driven entirely by the config list
 * {@code entities.assimilated_wizard.spawning.assimilationMap}, so a pack can retarget or extend
 * the mapping - Ancient Spellcraft class wizards included - without a rebuild. Each entry may name
 * a minimum {@link SimWizardTier}, which is how an evil or class wizard produces a stronger
 * parasite than a plain one.
 *
 * <p>The sim_wizard target can still be disabled wholesale via
 * {@code ModConfig.entities.assimilatedWizard.spawning.enabled}; in that case EVERY mapped source
 * falls back to sim_human, exactly as before.
 */
public final class SrpWizardryAssimilationHelper {

    private static final ResourceLocation SIM_WIZARD_ID = new ResourceLocation(InsaneTweaksMod.MODID, "sim_wizard");
    private static final ResourceLocation TEST_SIM_HUMAN_ID = new ResourceLocation("srparasites", "sim_human");

    /** Parsed form of the config map; rebuilt on demand, replaced atomically. */
    private static volatile java.util.Map<String, Mapping> mappings;

    /** One parsed row of the assimilation map. */
    private static final class Mapping {
        final ResourceLocation target;
        final SimWizardTier tierFloor;

        Mapping(ResourceLocation target, SimWizardTier tierFloor) {
            this.target = target;
            this.tierFloor = tierFloor;
        }
    }

    private SrpWizardryAssimilationHelper() {
    }

    /** Drops the parsed map so config edits apply without a restart. */
    public static void invalidateCache() {
        mappings = null;
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
        Mapping mapping = resolveMapping(originalId);
        if (mapping == null) {
            return false;
        }
        ResourceLocation targetId = mapping.target;

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

        // Tier floor from the mapping. Applied AFTER onInitialSpawn (which rolls the tier) so an
        // evil or class wizard can never come back weaker than its origin implies; setTierFloor
        // only ever raises, and re-applies the attribute scaling from pristine base values.
        if (mapping.tierFloor != null && converted instanceof EntitySimWizard) {
            ((EntitySimWizard) converted).setTierFloor(mapping.tierFloor);
        }

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

    private static Mapping resolveMapping(ResourceLocation originalId) {
        if (originalId == null) {
            return null;
        }
        java.util.Map<String, Mapping> map = mappings;
        if (map == null) {
            map = parseMappings();
            mappings = map;
        }
        Mapping mapping = map.get(originalId.toString());
        if (mapping == null) {
            return null;
        }
        // Master switch: when the custom entity is off, every mapped source becomes a plain
        // assimilated human instead, and the tier floor is meaningless.
        if (!ModConfig.entities.assimilatedWizard.spawning.enabled
                && SIM_WIZARD_ID.toString().equals(mapping.target.toString())) {
            return new Mapping(TEST_SIM_HUMAN_ID, null);
        }
        return mapping;
    }

    /** Parses {@code "<source id>=<target id>[:<TIER>]"} rows; bad rows are logged and skipped. */
    private static java.util.Map<String, Mapping> parseMappings() {
        java.util.Map<String, Mapping> map = new java.util.HashMap<String, Mapping>();
        String[] entries = ModConfig.entities.assimilatedWizard.spawning.assimilationMap;
        if (entries == null) {
            return java.util.Collections.unmodifiableMap(map);
        }
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            int split = entry.indexOf('=');
            if (split <= 0 || split == entry.length() - 1) {
                InsaneTweaksMod.LOGGER.warn(
                        "[IT][AssimilationBridge] Malformed assimilationMap entry '{}' - expected"
                                + " '<source id>=<target id>[:<TIER>]'. Skipped.", entry);
                continue;
            }
            String source = entry.substring(0, split).trim();
            String rest = entry.substring(split + 1).trim();

            SimWizardTier floor = null;
            int tierSplit = rest.indexOf(':', rest.indexOf(':') + 1); // skip the namespace colon
            if (tierSplit > 0) {
                String tierName = rest.substring(tierSplit + 1).trim();
                floor = SimWizardTier.byName(tierName);
                if (floor == null) {
                    InsaneTweaksMod.LOGGER.warn(
                            "[IT][AssimilationBridge] Unknown tier '{}' in '{}' - mapping kept without a floor.",
                            tierName, entry);
                }
                rest = rest.substring(0, tierSplit).trim();
            }

            if (rest.isEmpty()) {
                InsaneTweaksMod.LOGGER.warn(
                        "[IT][AssimilationBridge] Entry '{}' has no target id. Skipped.", entry);
                continue;
            }
            map.put(source, new Mapping(new ResourceLocation(rest), floor));
        }
        return java.util.Collections.unmodifiableMap(map);
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
