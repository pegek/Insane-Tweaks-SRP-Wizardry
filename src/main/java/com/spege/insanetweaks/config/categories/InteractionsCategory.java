package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.common.Loader;

/**
 * Cross-mod interaction toggles that depend on a specific optional mod being present.
 * Each master switch here gates a family of behaviors tied to one dependency.
 */
public class InteractionsCategory {

    @Config.Comment({
            "Master switch for all Enigmatic Legacy interactions.",
            "When ON, the mod integrates with Enigmatic Legacy in two ways:",
            "  - the Cursed Ring minion fix (see 'Enable Cursed Ring Minion Fix' in tweaks), and",
            "  - the Blessed Ring requirement that unlocks Bauble Fruit acquisition",
            "    (Corrupted Seed Fragment drops and Corrupted Sapling growth).",
            "When OFF, none of the above apply, even if Enigmatic Legacy is installed.",
            "Default is auto-detected: ON when Enigmatic Legacy is present, OFF otherwise." })
    @Config.Name("Enable Enigmatic Legacy Interactions")
    public boolean enableEnigmaticLegacyInteractions = Loader.isModLoaded("enigmaticlegacy");
}
