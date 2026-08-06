package com.spege.reskilltweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Kept as a category called {@code modules} holding a flag called {@code Enable Skills Module} so a
 * pack that already tuned this in {@code insanetweaks.cfg} migrates by copying the value across
 * rather than hunting for a renamed switch. Same reasoning as {@code traits} and
 * {@code scarredFlesh}, which also keep their old category names.
 */
public class ModulesCategory {

    @Config.Comment({
            "Enables the custom Reskillable trait module: 20 traits across all eight skill trees,",
            "the rewritten descriptions for the two native traits, and the tuned reskillable.cfg.",
            "This is the whole mod - with it off, installing this jar does nothing at all, which is",
            "why it defaults ON. (It was OFF in insanetweaks.cfg before the 2026-08-06 split, but",
            "there it was one optional module among a dozen; here it is the reason the jar exists.)",
            "NOTE what turning it on does beyond adding traits: on the next launch the mod replaces",
            "reskillable.cfg with its own tuned version. Your existing file is backed up first, to",
            "config/reskillable.install.v<version>.<yyyyMMdd-HHmmss>.cfg, and any custom Skill Locks you",
            "added are carried across. If you would rather keep your own reskillable.cfg untouched,",
            "turn this off - there is no separate switch for the swap.",
            "The mod preserves the legacy compatskills namespace for save compatibility.",
            "Requires a restart. Default ON." })
    @Config.Name("Enable Skills Module")
    @Config.RequiresMcRestart
    public boolean enableSkillsModule = true;

    @Config.Comment({
            "Adds a middle-click (and raw Ctrl + left click) fallback for refunding an unlocked",
            "trait in Reskillable's skill GUI, for packs where the native Ctrl detection is broken.",
            "Read live." })
    @Config.Name("Enable GUI Refund Fallback")
    public boolean enableGuiRefundFallback = true;
}
