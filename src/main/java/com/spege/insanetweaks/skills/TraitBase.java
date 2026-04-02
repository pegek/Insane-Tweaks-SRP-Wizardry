package com.spege.insanetweaks.skills;

import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Trait;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.api.ReskillableRegistries;
import com.spege.insanetweaks.config.ModConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Base class for all InsaneTweaks custom Reskillable traits.
 *
 * Each trait reads its cost, parent skill and requirements from the corresponding
 * ModConfig.traits.<field> (TraitConfig) at construction time.
 * Falls back to hardcoded defaults if the config field is null or malformed.
 */
@SuppressWarnings("null")
public abstract class TraitBase extends Trait {

    public TraitBase(String name, int x, int y, ModConfig.TraitConfig cfg, String defaultSkillName, int defaultCost, String... defaultRequirements) {
        super(
            new ResourceLocation(SkillsModule.DOMAIN, name),
            x, y,
            (cfg != null && cfg.parentSkill != null && !cfg.parentSkill.isEmpty()) ? new ResourceLocation("reskillable", cfg.parentSkill) : new ResourceLocation(defaultSkillName),
            (cfg != null) ? cfg.cost : defaultCost,
            (cfg != null && cfg.requirements != null && cfg.requirements.length > 0) ? cfg.requirements : defaultRequirements
        );
        SkillsModule.TRAITS.add(this);
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
