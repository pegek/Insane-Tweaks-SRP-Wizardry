package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class EffectsCategory {

    @Config.Comment({
            "Whether potion effects from other mods can regenerate the player's mana pool.",
            "Works live, no restart."})
    public boolean enabled = true;

    @Config.Comment({
            "Potion effects that regenerate mana, as `namespace:path=manaPerSecond`.",
            "Works live, no restart.",
            "",
            "These exist because the effects they name do NOT feed the player - they refill an ITEM.",
            "Ancient Spellcraft's Mana Regeneration tops up a held mana-storing item, which nothing",
            "spends once ManaCore pays for spells out of the player's pool, so the effect became",
            "silently useless. This routes it to the pool instead. The original item top-up still",
            "happens and is simply harmless.",
            "",
            "Any potion from any mod works. `ebwizardry:font_of_mana` is deliberately NOT listed:",
            "despite the name it has nothing to do with mana in EBW 4.3.19 - its only listener",
            "shortens spell cooldowns - so adding it would invent a mechanic rather than repair one."})
    public String[] potionRegen = new String[] {
            "ancientspellcraft:mana_regeneration=2"};

    @Config.Comment({
            "Whether a higher effect level grants proportionally more mana. Works live, no restart.",
            "On: level I grants the configured amount, level II twice it, and so on. Off: every",
            "level grants the same amount."})
    public boolean scaleWithAmplifier = true;
}
