package com.spege.reskilltweaks.skills;

import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Trait;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.api.ReskillableRegistries;
import com.spege.reskilltweaks.config.categories.TraitsCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Base class for all InsaneTweaks custom Reskillable traits.
 *
 * Each trait reads its cost, parent skill and requirements from the corresponding
 * ReskillTweaksConfig.traits.<field> (TraitConfig) at construction time.
 * Falls back to hardcoded defaults if the config field is null or malformed.
 */
@SuppressWarnings("null")
public abstract class TraitBase extends Trait {

    public TraitBase(String name, int x, int y, TraitsCategory.TraitConfig cfg, String defaultSkillName, int defaultCost, String... defaultRequirements) {
        super(
            new ResourceLocation(SkillsModule.DOMAIN, name),
            x, y,
            (cfg != null && cfg.parentSkill != null && !cfg.parentSkill.isEmpty()) ? new ResourceLocation("reskillable", cfg.parentSkill) : new ResourceLocation(defaultSkillName),
            (cfg != null) ? cfg.cost : defaultCost,
            (cfg != null && cfg.requirements != null && cfg.requirements.length > 0) ? cfg.requirements : defaultRequirements
        );
        // The single gate for traits.Enabled. TRAITS is the only thing that ever reaches
        // Reskillable's Unlockable registry (SkillsModule.RegistryHandler iterates it), and the
        // handlers all ask hasTrait() by id, which answers false for anything unregistered. So
        // leaving a disabled trait out of this list removes it from the skill tree AND stops its
        // effect, with one condition and no per-handler changes.
        if (cfg == null || cfg.enabled) {
            SkillsModule.TRAITS.add(this);
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Safely checks if the given player has unlocked a specific trait.
     * Uses Reskillable's native sync — safe on both server and client sides.
     *
     * @deprecated for code inside this mod, use {@link TraitHandle} instead. Every call here
     *             re-parses both ids into {@code ResourceLocation}s (eight objects) and re-does
     *             both registry lookups, and every internal call site passes compile-time
     *             constants. All of them were migrated on 2026-08-04.
     *             <p>This overload is deliberately kept rather than deleted: it takes plain
     *             strings, which makes it the only sane entry point for GroovyScript in the pack.
     */
    @Deprecated
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
