package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Trait;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.api.ReskillableRegistries;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.ModConfig.TraitConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Base class for all InsaneTweaks custom Reskillable traits.
 *
 * Each trait reads its cost, parent skill and requirements from the corresponding
 * ModConfig.traits.<field> (TraitConfig) at construction time.
 * Falls back to hardcoded defaults if the config field is null or malformed.
 */
public abstract class TraitBase extends Trait {

    public TraitBase(String name, int x, int y, String defaultSkillName, int defaultCost, String... defaultRequirements) {
        super(
            new ResourceLocation(SkillsModule.DOMAIN, name),
            x, y,
            resolveSkill(name, defaultSkillName),
            resolveCost(name, defaultCost),
            resolveRequirements(name, defaultRequirements)
        );
        SkillsModule.TRAITS.add(this);
    }

    // -------------------------------------------------------------------------
    // Config resolvers — map trait ID to the correct ModConfig.traits field
    // -------------------------------------------------------------------------

    private static TraitConfig getConfig(String traitId) {
        ModConfig.Traits t = ModConfig.traits;
        switch (traitId) {
            case "fast_learner":       return t.fastLearner;
            case "poison_immunity":    return t.poisonImmunity;
            case "iron_stomach":       return t.ironStomach;
            case "double_loot":        return t.doubleLoot;
            case "enchant_fishing":    return t.enchantFishing;
            case "astral_prospector":  return t.astralProspector;
            case "supreme_enchanter":  return t.supremeEnchanter;
            case "meditation":         return t.meditation;
            case "arcane_mastery":     return t.arcaneMastery;
            case "school_of_alteration":   return t.schoolOfAlteration;
            case "school_of_conjuration":  return t.schoolOfConjuration;
            case "school_of_destruction":  return t.schoolOfDestruction;
            case "power_creep":        return t.powerCreep;
            default: return null;
        }
    }

    private static ResourceLocation resolveSkill(String traitId, String defaultSkill) {
        TraitConfig cfg = getConfig(traitId);
        if (cfg != null && cfg.parentSkill != null && !cfg.parentSkill.isEmpty()) {
            return new ResourceLocation("reskillable", cfg.parentSkill);
        }
        return new ResourceLocation(defaultSkill);
    }

    private static int resolveCost(String traitId, int defaultCost) {
        TraitConfig cfg = getConfig(traitId);
        if (cfg != null) {
            return cfg.cost;
        }
        return defaultCost;
    }

    private static String[] resolveRequirements(String traitId, String[] defaultReqs) {
        TraitConfig cfg = getConfig(traitId);
        if (cfg != null && cfg.requirements != null && cfg.requirements.length > 0) {
            return cfg.requirements;
        }
        return defaultReqs;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Safely checks if the given player has unlocked a specific trait.
     * Uses Reskillable's native sync — safe on both server and client sides.
     */
    public static boolean hasTrait(EntityPlayer player, String skillId, String unlockableId) {
        try {
            PlayerData playerData = PlayerDataHandler.get(player);
            if (playerData == null) return false;

            Skill skill = ReskillableRegistries.SKILLS.getValue(new ResourceLocation(skillId));
            if (skill == null) return false;

            PlayerSkillInfo skillInfo = playerData.getSkillInfo(skill);
            if (skillInfo == null) return false;

            Unlockable unlockable = ReskillableRegistries.UNLOCKABLES.getValue(new ResourceLocation(unlockableId));
            if (unlockable == null) return false;

            return skillInfo.isUnlocked(unlockable);
        } catch (Exception e) {
            return false;
        }
    }
}
