package com.spege.reskilltweaks.config.categories;

import com.spege.reskilltweaks.config.TunedConfigMode;

import net.minecraftforge.common.config.Config;

/**
 * Kept as a category called {@code modules} holding a flag called {@code Enable Skills Module} so a
 * pack that already tuned this in {@code insanetweaks.cfg} migrates by copying the value across
 * rather than hunting for a renamed switch. Same reasoning as {@code traits} and
 * {@code scarredFlesh}, which also keep their old category names.
 */
public class ModulesCategory {

    @Config.Comment({
            "Enables the custom Reskillable trait module: up to 20 traits across all eight skill",
            "trees and the rewritten descriptions for the two native traits. 11 of the traits need",
            "nothing but Reskillable; the rest appear only when the mod they build on is installed",
            "(Electroblob's Wizardry, Scape and Run: Parasites, Insane Tweaks).",
            "This is the whole mod - with it off, installing this jar does nothing at all, which is",
            "why it defaults ON. (It was OFF in insanetweaks.cfg before the 2026-08-06 split, but",
            "there it was one optional module among a dozen; here it is the reason the jar exists.)",
            "Whether it also replaces reskillable.cfg with its tuned version is a separate",
            "choice - see Tuned Reskillable Config below.",
            "The mod preserves the legacy compatskills namespace for save compatibility.",
            "Requires a restart. Default ON." })
    @Config.Name("Enable Skills Module")
    @Config.RequiresMcRestart
    public boolean enableSkillsModule = true;

    @Config.Comment({
            "Whether to replace reskillable.cfg with the tuned version this mod ships (its own skill",
            "costs, level requirements and item locks, balanced around its traits).",
            "  ASK   - ask once on the main menu. A dedicated server cannot ask, so it only logs how to",
            "          choose and leaves reskillable.cfg alone until you do.",
            "  APPLY - install it, and re-install it whenever this mod's version changes. Your file is",
            "          backed up first, to config/reskillable.install.v<version>.<yyyyMMdd-HHmmss>.cfg,",
            "          and any custom Skill Locks you added are carried across.",
            "  KEEP  - never touch reskillable.cfg.",
            "If the tuned config has been installed before (config/insanetweaks_reskillable_managed.flag",
            "exists), ASK is treated as APPLY and saved as such - nothing is asked on an install that",
            "already chose it.",
            "The file is swapped during startup, before Reskillable reads it, so a choice made on the",
            "main menu takes effect on the next launch. Ignored while the skills module is off." })
    @Config.Name("Tuned Reskillable Config")
    @Config.RequiresMcRestart
    public TunedConfigMode tunedReskillableConfig = TunedConfigMode.ASK;

    @Config.Comment({
            "Adds a middle-click (and raw Ctrl + left click) fallback for refunding an unlocked",
            "trait in Reskillable's skill GUI, for packs where the native Ctrl detection is broken.",
            "Read live." })
    @Config.Name("Enable GUI Refund Fallback")
    public boolean enableGuiRefundFallback = true;
}
