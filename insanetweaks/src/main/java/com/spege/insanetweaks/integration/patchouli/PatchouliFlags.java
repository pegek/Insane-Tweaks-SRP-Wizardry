package com.spege.insanetweaks.integration.patchouli;

import com.spege.insanetweaks.config.ModConfig;

import vazkii.patchouli.api.PatchouliAPI;

/**
 * Hands the Codex the config switches its entries are flagged on.
 *
 * <p>🚨 Names Patchouli types, so it must only be reached from behind
 * {@code Loader.isModLoaded("patchouli")} - the call site in {@code InsaneTweaksMod.init} does that.
 */
public final class PatchouliFlags {

    /** Flag on the Codex entry listing the experimental trinket-part rituals. */
    public static final String TEST_RECIPES = "insanetweaks:altar_test_recipes";

    private PatchouliFlags() {
    }

    public static void apply() {
        PatchouliAPI.instance.setConfigFlag(TEST_RECIPES, ModConfig.tweaks.altarTestRecipes);
    }
}
