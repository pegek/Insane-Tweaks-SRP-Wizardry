package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ModulesCategory {
    @Config.Comment("Enables the SRParasites and Electroblob's Wizardry Bridge. This includes the evolving Living Armor, Sentient Spellblades, and the Sentient Aegis shield.")
    @Config.Name("Enable SRP-EBWizardry Bridge")
    @Config.RequiresMcRestart
    public boolean enableSrpEbWizardryBridge = true;

    @Config.Comment("Enables the custom Reskillable trait module. Requires Reskillable. The mod preserves the legacy compatskills namespace for save compatibility and may update reskillable.cfg with an automatic backup.")
    @Config.Name("Enable Skills Module")
    @Config.RequiresMcRestart
    public boolean enableSkillsModule = false;

    @Config.Comment("Registers Cost, Potency and Speedcast Core items for use with Electroblob's Wizardry armor upgrades. Disable if you don't use EBWizardry.")
    @Config.Name("Enable Custom Cores (EBWizardry)")
    @Config.RequiresMcRestart
    public boolean enableCustomCores = true;

    @Config.Comment("Registers all InsaneTweaks custom Electroblob's Wizardry spells. Disable this to remove every spell added by the mod from the spell registry.")
    @Config.Name("Enable Spells")
    @Config.RequiresMcRestart
    public boolean enableSpells = true;

    @Config.Comment({
            "Enables the Bauble Fruit system. Bauble Fruits are one-time consumable items that permanently",
            "grant the player an additional bauble slot when eaten.",
            "Requires BaublesEX (v2.0.0+) to function. Does nothing if only the original Baubles (v1.5.x) is installed." })
    @Config.Name("Enable Bauble Fruits")
    @Config.RequiresMcRestart
    public boolean enableBaubleFruits = true;

    @Config.Comment({
            "Registers the Sentient Codex enchantment (native port of UniqueEnchantments' Grimoire) and its",
            "runtime handler. VERY_RARE, reward-only: it dynamically raises the level of every other",
            "enchantment on the item as the holder's XP level grows, with owner-binding and drop/anvil",
            "protection. Tunables live in the 'sentientcodex' category. Disable to remove it entirely." })
    @Config.Name("Enable Sentient Codex Enchantment")
    @Config.RequiresMcRestart
    public boolean enableSentientCodex = true;

    @Config.Comment({
            "Enables the Mmmm enchantment's runtime (native port of UniqueEnchantments' Ambrosia). Food only:",
            "eating an enchanted stack fills the hunger bar and grants Nourished, which keeps saturation pinned",
            "for a duration that scales with the eater's XP level. Tunables live in the 'enchantments.mmmm'",
            "category. Read live: the enchantment and the Nourished effect stay registered either way, so",
            "turning this off never breaks an existing world - it only stops the effect from being applied." })
    @Config.Name("Enable Mmmm Enchantment")
    public boolean enableMmmm = true;

    @Config.Comment({
            "Enables Property Books: combine a tool and a Property Book on an anvil to grant that ONE item",
            "an advanced property (the 'Properties:' block in its tooltip). Ships with Ashen Legacy (the",
            "dropped item survives fire, lava and explosions) and Grip (the weapon cannot leave your",
            "inventory - the same mechanic an evolved Sentient Spellblade earns on its own). Tunables live",
            "in the 'propertyBooks' category.",
            "This gates the anvil recipe only. The book item itself stays registered either way, so turning",
            "this off never makes books already in a world disappear." })
    @Config.Name("Enable Property Books")
    @Config.RequiresMcRestart
    public boolean enablePropertyBooks = true;

    @Config.Comment({
            "Enables the Auto Lock Picker: an item that opens a Locks lock by holding right-click on it,",
            "instead of playing Locks' pin minigame. Channel time and durability cost scale with the",
            "lock's pin count; the lock's Complexity, Sturdy and Shocking enchantments all still apply.",
            "Requires the Locks mod - does nothing without it. Tunables live in the 'autoLockPicker'",
            "category, the Swift Picking enchantment in 'enchantments'. Read live: the item and the",
            "enchantment stay registered either way, so turning this off never breaks an existing world." })
    @Config.Name("Enable Auto Lock Picker")
    public boolean enableAutoLockPicker = true;

    @Config.Comment({
            "Enables the Sanctuary Dome: a pyramid-based core block that blocks SRParasites spawning",
            "and terrain infestation in a cylindrical region, and slowly reverts existing infestation",
            "(fuel-powered cleanse). Requires SRParasites; auto-disabled if absent." })
    @Config.Name("Enable Sanctuary Dome")
    @Config.RequiresMcRestart
    public boolean enableSanctuary = true;
}
