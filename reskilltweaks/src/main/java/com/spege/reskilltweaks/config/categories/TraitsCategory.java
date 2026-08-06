package com.spege.reskilltweaks.config.categories;

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

    @Config.Name("Stone Fists")
    public TraitConfig stoneFists = new TraitConfig(14, "mining",
            new String[] { "reskillable:mining|30", "reskillable:gathering|20", "reskillable:building|20" });

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

    @Config.Name("Coiled Spring")
    public TraitConfig coiledSpring = new TraitConfig(8, "agility",
            new String[] { "reskillable:agility|40" });

    @Config.Name("Scarred Flesh")
    public TraitConfig scarredFlesh = new TraitConfig(15, "defense",
            new String[] { "reskillable:defense|40" });

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

    @Config.Name("Astral Prospector - Extra Ore Blocks")
    @Config.Comment({
            "Block ids Astral Prospector should treat as ore even though nothing else identifies them.",
            "Format: modid:block_name (e.g. srparasites:infestedore).",
            "Only needed for blocks that are BOTH absent from the OreDictionary and named in a way",
            "the shape test cannot see - i.e. 'ore' in the middle of a word with no underscore, like",
            "'crystalore'. Ancient Spellcraft's crystal_ore_* and anything ending in _ore are already",
            "covered without a config entry. Read live, no restart needed."
    })
    public String[] astralProspectorExtraOres = new String[] {};

    // ========================================================================
    // TRAIT CONFIG HELPER
    // ========================================================================
    public static class TraitConfig {
        @Config.Name("Enabled")
        @Config.Comment({
                "Whether this trait exists at all.",
                "Turn this OFF and the trait is not added to the skill tree: nobody can buy it, and",
                "its effect stops working for everyone who already had it.",
                "Read this before switching one off on a world you care about. If a player has",
                "ALREADY bought this trait, their unlock is quietly dropped the next time their",
                "character data is loaded, and the skill points they paid for it are NOT refunded.",
                "Nothing crashes and nothing else on the character is touched - but those points are",
                "gone, and turning the trait back on later does not bring the unlock back, because by",
                "then it has already been erased from the save. If you disable a trait part-way",
                "through a world, hand the affected players their points back yourself.",
                "Requires a restart. Default ON."
        })
        @Config.RequiresMcRestart
        public boolean enabled = true;

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

        /** Keeps the 21 declarations above unchanged; {@link #enabled} defaults to true. */
        public TraitConfig(int cost, String parentSkill, String[] requirements) {
            this(cost, parentSkill, requirements, true);
        }

        public TraitConfig(int cost, String parentSkill, String[] requirements, boolean enabled) {
            this.cost = cost;
            this.parentSkill = parentSkill;
            this.requirements = requirements;
            this.enabled = enabled;
        }
    }
}
