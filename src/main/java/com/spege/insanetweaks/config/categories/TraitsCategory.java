package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class TraitsCategory {
    @Config.Name("Fast Learner")
    public TraitConfig fastLearner = new TraitConfig(6, "attack", new String[] { "reskillable:attack|8" });

    @Config.Name("Assimilated Warfare")
    public TraitConfig assimilatedWarfare = new TraitConfig(6, "attack",
            new String[] { "reskillable:attack|18" });

    @Config.Name("Spider's Grace")
    public TraitConfig spidersGrace = new TraitConfig(7, "defense",
            new String[] { "reskillable:defense|35" });

    @Config.Name("Iron Stomach")
    public TraitConfig ironStomach = new TraitConfig(5, "defense",
            new String[] { "reskillable:defense|15" });

    @Config.Name("Double Loot")
    public TraitConfig doubleLoot = new TraitConfig(5, "gathering",
            new String[] { "reskillable:gathering|10" });

    @Config.Name("Enchant Fishing")
    public TraitConfig enchantFishing = new TraitConfig(7, "gathering",
            new String[] { "reskillable:gathering|32" });

    @Config.Name("Astral Prospector")
    public TraitConfig astralProspector = new TraitConfig(7, "mining",
            new String[] { "reskillable:mining|30" });

    @Config.Name("Supreme Enchanter")
    public TraitConfig supremeEnchanter = new TraitConfig(8, "building",
            new String[] { "reskillable:building|30" });

    @Config.Name("Bob the Builder")
    public TraitConfig bobTheBuilder = new TraitConfig(5, "building",
            new String[] { "reskillable:building|18" });

    @Config.Name("Angry Farmer")
    public TraitConfig angryFarmer = new TraitConfig(10, "farming",
            new String[] { "reskillable:farming|45" });

    @Config.Name("Adapted Vegetation")
    public TraitConfig adaptedVegetation = new TraitConfig(5, "farming",
            new String[] { "reskillable:farming|18" });

    @Config.Name("Meditation")
    public TraitConfig meditation = new TraitConfig(6, "agility",
            new String[] { "reskillable:agility|18", "reskillable:magic|10" });

    @Config.Name("Arcane Mastery")
    public TraitConfig arcaneMastery = new TraitConfig(5, "magic", new String[] { "reskillable:magic|18" });

    @Config.Name("School of Alteration")
    public TraitConfig schoolOfAlteration = new TraitConfig(5, "magic",
            new String[] { "reskillable:magic|28" });

    @Config.Name("School of Conjuration")
    public TraitConfig schoolOfConjuration = new TraitConfig(5, "magic",
            new String[] { "reskillable:magic|22" });

    @Config.Name("School of Destruction")
    public TraitConfig schoolOfDestruction = new TraitConfig(5, "magic",
            new String[] { "reskillable:magic|22" });

    @Config.Name("Archmage")
    public TraitConfig archmage = new TraitConfig(8, "magic", new String[] { "reskillable:magic|45" });

    // ========================================================================
    // TRAIT CONFIG HELPER
    // ========================================================================
    public static class TraitConfig {
        @Config.Name("SP Cost")
        @Config.Comment("Skill point cost to unlock this trait.")
        @Config.RequiresMcRestart
        public int cost;

        @Config.Name("Parent Skill Tree")
        @Config.Comment("The Reskillable skill tree this trait belongs to (e.g. magic, attack, defense).")
        @Config.RequiresMcRestart
        public String parentSkill;

        @Config.Name("Requirements")
        @Config.Comment("List of required skills and levels. Format: reskillable:skill_name|level")
        @Config.RequiresMcRestart
        public String[] requirements;

        public TraitConfig(int cost, String parentSkill, String[] requirements) {
            this.cost = cost;
            this.parentSkill = parentSkill;
            this.requirements = requirements;
        }
    }
}
