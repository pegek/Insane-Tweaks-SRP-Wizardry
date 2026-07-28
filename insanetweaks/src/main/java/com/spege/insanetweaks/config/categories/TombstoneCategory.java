package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class TombstoneCategory {
    @Config.Comment("Master switch for all Corail Tombstone tweaks. If false, no Tombstone related tweaks will be applied.")
    @Config.Name("Enable Tombstone Tweaks")
    @Config.RequiresMcRestart
    public boolean enableTombstoneTweaks = false;

    @Config.Comment({
            "Fixes the Curse of Possession exploit with Corail Tombstone.",
            "Cursed items will now properly vanish on death instead of hiding in your grave.",
            "Use /itweaks restore cursed <player> to view/restore backups."
    })
    @Config.Name("Enable Curse of Possession Patch")
    @Config.RequiresMcRestart
    public boolean enableCurseOfPossessionPatch = false;

    @Config.Comment("Removes the vanilla Tombstone recipe to craft an Enchanted Grave Key using an Ender Pearl.")
    @Config.Name("Disable Enchant Key Recipe")
    @Config.RequiresMcRestart
    public boolean disableEnchantKeyRecipe = true;

    @Config.Comment({
            "Adds a Knowledge of Death tab under Reskillable's inventory tabs, opening Tombstone's",
            "perk tree without the keybind. Needs both Reskillable and Tombstone; also follows",
            "Reskillable's own \"Enable Reskillable Tabs\" switch."
    })
    @Config.Name("Enable Knowledge Inventory Tab")
    @Config.RequiresMcRestart
    public boolean enableKnowledgeTab = true;

    // ----------------------------------------------------------------
    // GRAVE ITEM DECAY
    // ----------------------------------------------------------------
    @Config.Comment({
            "Enable grave item decay \u2014 after despawn delay, grave loses 1 random stack every interval.",
            "Use /itweaks restore decay <player> to view/restore items lost to grave decay."
    })
    @Config.Name("Enable Grave Item Decay")
    public boolean enableGraveItemDecay = false;

    @Config.Comment("Ticks before decay starts. Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
    @Config.Name("Grave Decay Start Delay (ticks)")
    @Config.RangeInt(min = 0, max = 192000)
    public int graveDecayStartTicks = 6000;

    @Config.Comment("Interval in ticks between each item removal. 1200 = 60 seconds.")
    @Config.Name("Grave Decay Interval (ticks)")
    @Config.RangeInt(min = 20, max = 24000)
    public int graveDecayIntervalTicks = 1200;

    @Config.Comment("Max number of decay snapshots kept per player. Oldest entries are removed when exceeded.")
    @Config.Name("Max Grave Decay Backup History")
    @Config.RangeInt(min = 1, max = 50)
    public int graveDecayMaxHistory = 10;

    // ----------------------------------------------------------------
    // MECHANICS NERFS
    // ----------------------------------------------------------------
    @Config.Comment("Reduces the chance for grave_dust to drop from undead mobs.")
    @Config.Name("Nerf Grave Dust Drop Rate")
    public boolean nerfGraveDustDrop = false;

    @Config.Comment("The percentage chance (0-100) for a grave_dust drop to be kept. 100 = native rate(10%), 0 = never drops.")
    @Config.Name("Grave Dust Drop Chance (%)")
    @Config.RangeInt(min = 0, max = 100)
    public int graveDustDropChance = 100;

    @Config.Comment("Cooldown in minutes before the Book of Disenchantment can be used again. 0 disables the cooldown. Max 720 = 12 h.")
    @Config.Name("Book of Disenchantment Cooldown (Minutes)")
    @Config.RangeInt(min = 0, max = 720)
    public int bookOfDisenchantmentCooldownMinutes = 6;

    @Config.Comment("Cooldown in minutes before the Book of Magic Impregnation can be used again. 0 disables the cooldown. Max 720 = 12 h.")
    @Config.Name("Book of Magic Impregnation Cooldown (Minutes)")
    @Config.RangeInt(min = 0, max = 720)
    public int bookOfMagicImpregnationCooldownMinutes = 6;

    // ----------------------------------------------------------------
    // KNOWLEDGE OF DEATH - PERK SETTINGS
    // Each perk has: enabled (bool) and maxLevel (int capped at native max)
    // Native max levels: alchemist=5, concentration=2, gladiator=5,
    // jailer=5, memento_mori=dynamic, rune_inscriber=5, scribe=5,
    // shadow_walker=5, treasure_seeker=5, witch_doctor=5
    // ----------------------------------------------------------------
    @Config.Name("Perk: Alchemist")
    @Config.Comment("Controls the Alchemist perk (scroll duration bonus). Native max level: 5")
    public PerkConfig alchemist = new PerkConfig(true, 5);

    @Config.Name("Perk: Concentration")
    @Config.Comment("Controls the Concentration perk (soul gathering bonus). Native max level: 2")
    public PerkConfig concentration = new PerkConfig(true, 2);

    @Config.Name("Perk: Gladiator")
    @Config.Comment("Controls the Gladiator perk (combat bonuses). Native max level: 5")
    public PerkConfig gladiator = new PerkConfig(true, 5);

    @Config.Name("Perk: Jailer")
    @Config.Comment("Controls the Jailer perk (enchanted grave key chance). Native max level: 5 (dynamic, depends on Tombstone's chanceEnchantedGraveKey config)")
    public PerkConfig jailer = new PerkConfig(true, 5);

    @Config.Name("Perk: Memento Mori")
    @Config.Comment("Controls the Memento Mori perk (XP loss reduction). Native max level is dynamic (depends on xpLoss config). Set maxLevel to 0 to equivalent disable.")
    public PerkConfig mementoMori = new PerkConfig(true, 5);

    @Config.Name("Perk: Rune Inscriber")
    @Config.Comment("Controls the Rune Inscriber perk (tablet cooldown reduction). Native max level: 5")
    public PerkConfig runeInscriber = new PerkConfig(true, 5);

    @Config.Name("Perk: Scribe")
    @Config.Comment("Controls the Scribe perk (book of disenchantment bonus uses). Native max level: 5")
    public PerkConfig scribe = new PerkConfig(true, 5);

    @Config.Name("Perk: Shadow Walker")
    @Config.Comment("Controls the Shadow Walker perk (ghostly shape efficiency). Native max level: 5")
    public PerkConfig shadowWalker = new PerkConfig(true, 5);

    @Config.Name("Perk: Treasure Seeker")
    @Config.Comment("Controls the Treasure Seeker perk (grave loot bonuses). Native max level: 5")
    public PerkConfig treasureSeeker = new PerkConfig(true, 5);

    @Config.Name("Perk: Witch Doctor")
    @Config.Comment("Controls the Witch Doctor perk (voodoo poppet efficiency). Native max level: 5")
    public PerkConfig witchDoctor = new PerkConfig(true, 5);

    // ----------------------------------------------------------------
    // CUSTOM PERKS ADDED BY THIS MOD
    // Registered into Tombstone's own tombstone:perks Forge registry, so they appear in the
    // Knowledge of Death GUI, are purchased, persisted and synced by Tombstone itself.
    // Tombstone never runs their effect — that lives in our own handlers.
    //
    // Note the perks are ALWAYS registered when Tombstone is present, regardless of these
    // flags. A registry object hidden behind a config flag vanishes from an existing world;
    // 'Enabled' greys the perk out via isDisabled instead.
    // ----------------------------------------------------------------
    @Config.Name("Custom Perk: Assimilated Knowledge")
    @Config.Comment({"Killing high-tier parasites teaches you something about death itself.",
            "Each level adds a flat chance for a qualifying parasite kill to grant knowledge."})
    public AssimilatedKnowledgeConfig assimilatedKnowledge = new AssimilatedKnowledgeConfig();

    @Config.Name("Custom Perk: Relief for the Damned")
    @Config.Comment({"Softens every penalty of Enigmatic Legacy's Ring of the Seven Curses.",
            "Requires Enigmatic Legacy; the perk greys itself out when the mod is absent."})
    public ReliefForTheDamnedConfig reliefForTheDamned = new ReliefForTheDamnedConfig();

    // ========================================================================
    // PERK CONFIG HELPER
    // ========================================================================
    public static class PerkConfig {
        @Config.Name("Enabled")
        @Config.Comment("If false, this perk will be shown as disabled (greyed out) in the Knowledge of Death GUI and cannot be levelled.")
        public boolean enabled;

        @Config.Name("Max Level")
        @Config.Comment("Maximum level cap for this perk. Cannot exceed the native maximum defined by Tombstone. Set to 0 to effectively disable it via level cap.")
        @Config.RangeInt(min = 0, max = 5)
        public int maxLevel;

        public PerkConfig(boolean enabled, int maxLevel) {
            this.enabled = enabled;
            this.maxLevel = maxLevel;
        }
    }

    // ========================================================================
    // CUSTOM PERK CONFIGS
    // ========================================================================

    /**
     * Assimilated Knowledge: qualifying parasite kills feed Tombstone's knowledge economy.
     *
     * <p>Scale matters here. Tombstone derives perk points as {@code floor(sqrt(knowledge - 1))},
     * so knowledge 101 is only 10 points — a flat point per parasite would break the economy
     * within an hour. Hence a low per-level chance, restricted to high-tier parasites.
     */
    public static class AssimilatedKnowledgeConfig {

        @Config.Name("Enabled")
        @Config.Comment("If false the perk is greyed out in the Knowledge of Death GUI and grants nothing.")
        public boolean enabled = true;

        @Config.Name("Max Level")
        @Config.Comment("Maximum level of this perk. 0 effectively disables it.")
        @Config.RangeInt(min = 0, max = 5)
        public int maxLevel = 3;

        @Config.Name("Point Cost Per Level")
        @Config.Comment({"Perk points charged for each level of this perk.",
                "Matches the flat 1 point per level every native Tombstone perk uses. Raising it",
                "is the cheapest balance lever available, since no native perk overrides getCost."})
        @Config.RangeInt(min = 1, max = 10)
        public int pointCostPerLevel = 1;

        @Config.Name("Chance Per Level")
        @Config.Comment({"Chance, per perk level, that a qualifying parasite kill grants knowledge.",
                "0.30 at level 3 means a 90% chance per kill.",
                "TEMPORARY TEST VALUE - deliberately loud so the perk can be confirmed to fire at all.",
                "The intended shipping value is around 0.02 (6% at level 3); this drops back down",
                "during the perk rebalance pass."})
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double chancePerLevel = 0.30;

        @Config.Name("Knowledge Per Proc")
        @Config.Comment("Knowledge granted when the roll succeeds.")
        @Config.RangeInt(min = 1, max = 100)
        public int knowledgePerProc = 1;

        @Config.Name("Alignment Per Proc")
        @Config.Comment({"Alignment granted alongside the knowledge. 0 leaves alignment untouched.",
                "Negative values are allowed if you consider hive-learning a dark art."})
        @Config.RangeInt(min = -10, max = 10)
        public int alignmentPerProc = 0;

        @Config.Name("Qualifying Entities")
        @Config.Comment({"Registry-name prefixes of parasites whose death can grant knowledge.",
                "Exact names work too (a full name is its own prefix). Low-tier chaff is left out",
                "on purpose — it dies in the hundreds and would trivialise the knowledge economy."})
        public String[] qualifyingEntityPrefixes = {
                "srparasites:ada_", "srparasites:anc_",
                "srparasites:overseer", "srparasites:vigilante", "srparasites:warden",
                "srparasites:marauder", "srparasites:monarch",
                "srparasites:architect", "srparasites:succor", "srparasites:carrier_colony",
                "srparasites:wraith", "srparasites:haunter", "srparasites:seeker" };

        @Config.Name("Debug Logging")
        @Config.Comment("Log every qualifying kill and every successful roll to the server log.")
        public boolean debugLogging = false;
    }

    /**
     * Relief for the Damned: reduces each Cursed Ring penalty by a share of its own value.
     *
     * <p>Enigmatic Legacy exposes no API for this, so the reduction is applied by redirecting
     * the three {@code EnigmaticConfigs} constants at the exact instruction where the mod reads
     * them — after all of its own guards, before it computes anything.
     */
    public static class ReliefForTheDamnedConfig {

        @Config.Name("Enabled")
        @Config.Comment("If false the perk is greyed out and the Cursed Ring behaves natively.")
        public boolean enabled = true;

        @Config.Name("Max Level")
        @Config.Comment("Maximum level of this perk. 0 effectively disables it.")
        @Config.RangeInt(min = 0, max = 5)
        public int maxLevel = 3;

        @Config.Name("Point Cost Per Level")
        @Config.Comment({"Perk points charged for each level of this perk.",
                "Matches the flat 1 point per level every native Tombstone perk uses."})
        @Config.RangeInt(min = 1, max = 10)
        public int pointCostPerLevel = 1;

        @Config.Name("Reduction Per Level")
        @Config.Comment({"Share of each penalty waived per perk level.",
                "0.12 at level 3 waives 36% of every penalty: the ring's default 50% monster",
                "damage reduction becomes 32%, its 200% incoming damage becomes 164%, and its",
                "30% armour debuff becomes 19.2%.",
                "Applies to all three penalties equally."})
        @Config.RangeDouble(min = 0.0, max = 0.5)
        public double reductionPerLevel = 0.12;
    }
}
