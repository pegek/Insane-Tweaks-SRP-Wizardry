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
            "Registers the Grimoire enchantment (native port of UniqueEnchantments' Grimoire) and its",
            "runtime handler. VERY_RARE, reward-only: it dynamically raises the level of every other",
            "enchantment on the item as the holder's XP level grows, with owner-binding and drop/anvil",
            "protection. Tunables live in the 'grimoire' category. Disable to remove it entirely." })
    @Config.Name("Enable Grimoire Enchantment")
    @Config.RequiresMcRestart
    public boolean enableGrimoire = true;

    @Config.Comment({
            "Enables the Sanctuary Dome: a pyramid-based core block that blocks SRParasites spawning",
            "and terrain infestation in a cylindrical region, and slowly reverts existing infestation",
            "(fuel-powered cleanse). Requires SRParasites; auto-disabled if absent." })
    @Config.Name("Enable Sanctuary Dome")
    @Config.RequiresMcRestart
    public boolean enableSanctuary = true;
}
