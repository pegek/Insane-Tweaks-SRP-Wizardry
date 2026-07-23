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
}
