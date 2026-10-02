package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class AdvancementCategory {

    @Config.Comment({
            "Whether advancements grant permanent maximum mana. Works live, no restart.",
            "Switching this off does NOT freeze the bonus at its last value: the recalculation",
            "still runs and simply treats the table as empty, so the modifier is removed."})
    public boolean enabled = true;

    @Config.Comment({
            "Advancements that permanently raise maximum mana, as `namespace:path=amount`.",
            "Works live for anyone who logs in again afterwards - the bonus is recalculated from",
            "the advancements a player has actually completed, never banked, so lowering a value",
            "here (or removing a line) takes the mana back rather than leaving it granted forever.",
            "",
            "Any advancement from any mod works, not just Electroblob's Wizardry - the defaults",
            "below are simply where this pack starts. An id that no mod provides is ignored.",
            "Negative amounts are allowed, for an advancement that should COST maximum mana; the",
            "total is floored at zero either way.",
            "",
            "Deliberately omitted from the defaults: the eleven `ebwizardry:handbook/*` entries.",
            "They carry no display and exist to track reading progress inside the handbook, so a",
            "reward for them would be invisible to the player and simply confusing. Both `root`",
            "advancements are omitted for a related reason - they are granted for having the mod",
            "installed, not for doing anything.",
            "",
            "Trinkets and Baubles has only two advancements (one of them a Patchouli guide unlock)",
            "and Bountiful Baubles has none, so neither appears here."})
    public String[] bonuses = new String[] {
            "ebwizardry:crystal=5",
            "ebwizardry:arcane_initiate=5",
            "ebwizardry:apprentice=10",
            "ebwizardry:advanced=10",
            "ebwizardry:master=20",
            "ebwizardry:armour_set=10",
            "ebwizardry:special_upgrade=5",
            "ebwizardry:defeat_evil_wizard=5",
            "ebwizardry:visit_shrine=5",
            "ebwizardry:artefact=10",
            "ebwizardry:defeat_remnant=5",
            "ebwizardry:restore_imbuement_altar=10",
            "ebwizardry:max_out_wand=15",
            "ebwizardry:discover_master_spell=10",
            "ebwizardry:all_artefacts=15",
            "ebwizardry:all_spells=20",
            "ancientspellcraft:crystal_shards=5",
            "ancientspellcraft:relics=5",
            "ancientspellcraft:scribing_desk=5",
            "ancientspellcraft:battlemage_camp=5",
            "ancientspellcraft:ancient_book=10",
            "ancientspellcraft:sphere_of_cognizance=10",
            "ancientspellcraft:relic_research=10",
            "ancientspellcraft:novice_spellblade=10",
            "ancientspellcraft:grand_stone_tablet=10"};

    @Config.Comment({
            "Ceiling on the total granted by all advancements together. Works live, no restart.",
            "The defaults above sum to 230 (160 from Electroblob's Wizardry, 70 from Ancient",
            "Spellcraft), so the gap to this value is deliberate headroom for a pack to add its own",
            "entries without the total running away. Lowering this below the sum of the table is",
            "allowed and simply trims the total - but note the trimming is invisible in game: a",
            "player earning an advancement past the ceiling is told nothing and gains nothing."})
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double cap = 250.0D;

    @Config.Comment({
            "Whether to tell the player on the action bar when an advancement grants mana.",
            "On by default: without it the reward is invisible until the player happens to look",
            "at the bar and notice it got longer. Works live, no restart."})
    public boolean announce = true;
}
