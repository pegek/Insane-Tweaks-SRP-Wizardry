package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Top-level container for the mod's custom-enchantment tunables. Each enchantment gets its own
 * nested block (Forge recurses into public object fields, mirroring how {@code EntitiesCategory}
 * nests {@code assimilatedWizard}). Currently holds the Sentient Codex enchantment.
 *
 * <p>Accessed as {@code ModConfig.enchantments.sentientCodex.*}.
 */
public class EnchantmentsCategory {

    @Config.Name("sentientCodex")
    @Config.Comment("Sentient Codex enchantment (native port of UniqueEnchantments' Grimoire): boost formula, "
            + "per-enchant cap, owner-binding, drop/anvil protection. Master toggle is modules.enableSentientCodex.")
    public final SentientCodexCategory sentientCodex = new SentientCodexCategory();
}
