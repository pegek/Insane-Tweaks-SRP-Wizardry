package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ClientCategory {
    @Config.Comment({ "Hides the CLEANSE effect icon and text from the HUD and inventory effect panel.",
            "Enable this flag to suppress the effect from appearing in the GUI entirely.",
            "The mechanic itself still works regardless of this setting.",
            "Intended as a temporary toggle while the effect's visuals are being refined." })
    @Config.Name("Hide Cleanse Effect from GUI")
    public boolean hideCleanseHudEffect = false;

    @Config.Comment("Should mod display info whether he found any cursed items in players inventory on death")
    @Config.Name("Display info on death")
    public boolean displayInfoOnDeath = false;

    @Config.Comment("Whether to display debug info for various mod mechanics.")
    @Config.Name("Display DEBUG INFO")
    public boolean displayDebugInfo = false;

    @Config.Comment({
            "Enables verbose per-tick debug logging for Thrall AI tasks (woodcutting, mineshaft, navigation, deposit).",
            "Useful for diagnosing AI behavior. WARN-level entries are always logged regardless of this flag." })
    @Config.Name("Thrall AI Debug Logs")
    public boolean enableThrallDebugLogs = false;

    @Config.Comment({
            "Verbose diagnostic logging for the Sim Wizard casting pipeline: spell pool contents,",
            "target/cooldown gate rejections (throttled), spell picks and cast results.",
            "Turn on to diagnose 'wizard is not casting' reports, then send the log to the mod author." })
    @Config.Name("Sim Wizard Debug Logs")
    public boolean enableSimWizardDebugLogs = false;

    @Config.Comment({
            "Verbose diagnostic logging for the Sentinel combat pipeline: target gates,",
            "spell picks, cast results, melee/cast state transitions." })
    @Config.Name("Sentinel Debug Logs")
    public boolean enableSentinelDebugLogs = false;

    @Config.Comment({
            "If true, suppresses the mod-recommendation and version-warning messages that appear in chat",
            "when you join a world (e.g. missing optional mods, SRPextra version hints).",
            "Warnings are still written to the log file regardless of this setting."
    })
    @Config.Name("Suppress Startup Chat Warnings")
    public boolean suppressStartupWarningsInChat = false;
}
