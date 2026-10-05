package com.spege.ebreduxaddon.platform;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Cztery kategorie, plik config/ebreduxaddon-common.toml. Wszystko czytane na zywo, bo nic tu nie
 * steruje rejestracja.
 *
 * <p>Liczby zaklec (koszt, cooldown, obrazenia, zasieg) NIE sa tutaj: siedza w
 * data/ebreduxaddon/spells/*.json, tam gdzie Redux trzyma je dla swoich zaklec, i da sie je
 * nadpisac datapackiem.
 */
public final class Config {

    // [wand]
    public final ForgeConfigSpec.IntValue wandEvolveAt;
    public final ForgeConfigSpec.IntValue wandFungalKillPoints;
    public final ForgeConfigSpec.DoubleValue symbioticMaxCostReduction;
    public final ForgeConfigSpec.DoubleValue symbioticMaxDurationBonus;
    public final ForgeConfigSpec.DoubleValue sentientCostMultiplier;
    public final ForgeConfigSpec.DoubleValue sentientDurationMultiplier;
    public final ForgeConfigSpec.DoubleValue sentientPotencyMultiplier;

    // [armor]
    public final ForgeConfigSpec.IntValue armorEvolveAt;
    public final ForgeConfigSpec.DoubleValue graftedCostReductionPerPiece;
    public final ForgeConfigSpec.DoubleValue sentientCostReductionPerPiece;
    public final ForgeConfigSpec.DoubleValue sentientPotencyPerPiece;
    public final ForgeConfigSpec.BooleanValue lastStandEnabled;
    public final ForgeConfigSpec.DoubleValue lastStandHealth;
    public final ForgeConfigSpec.IntValue lastStandImmunityTicks;
    public final ForgeConfigSpec.IntValue lastStandCleanseTicks;
    public final ForgeConfigSpec.IntValue lastStandCooldownSeconds;

    // [spells]
    public final ForgeConfigSpec.IntValue spineVolleyPuddleEvery;
    public final ForgeConfigSpec.IntValue cleanseTickInterval;

    // [infectedWizard]
    public final ForgeConfigSpec.IntValue infectedSpellCount;
    public final ForgeConfigSpec.BooleanValue infectedAlwaysSpineVolley;
    public final ForgeConfigSpec.DoubleValue infectedWandDropChance;
    public final ForgeConfigSpec.BooleanValue infectedEvolutionEnabled;
    public final ForgeConfigSpec.DoubleValue infectedEvolveChance;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> infectedEvolutions;
    public final ForgeConfigSpec.DoubleValue mycomancerWardRadius;
    public final ForgeConfigSpec.IntValue mycomancerWardInterval;
    public final ForgeConfigSpec.IntValue mycomancerWardDuration;
    public final ForgeConfigSpec.IntValue mycomancerWardAmplifier;

    // [spore]
    public final ForgeConfigSpec.BooleanValue sporeEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> sporeCleansedEffects;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> sporePurifiedBlocks;

    private Config(ForgeConfigSpec.Builder b) {
        b.comment("Symbiotic Wand: grows with the mana you spend through it, then evolves into the Sentient Wand.")
         .push("wand");
        wandEvolveAt = b
                .comment("Symbiosis points at which a Symbiotic Wand evolves. One point per mana spent",
                         "on a spell cast with that wand.")
                .defineInRange("evolveAt", 6000, 1, 10_000_000);
        wandFungalKillPoints = b
                .comment("Extra points for killing a mob in the spore:fungus_entities tag while holding the wand.",
                         "Without Fungal Infection: Spore the tag is empty and this never triggers. 0 disables.")
                .defineInRange("fungalKillPoints", 40, 0, 1_000_000);
        symbioticMaxCostReduction = b
                .comment("Mana cost reduction of a Symbiotic Wand at full symbiosis. Grows linearly from 0.",
                         "0.15 = 15% cheaper.")
                .defineInRange("symbioticMaxCostReduction", 0.15, 0.0, 0.9);
        symbioticMaxDurationBonus = b
                .comment("Spell duration bonus of a Symbiotic Wand at full symbiosis. 0.25 = 25% longer.")
                .defineInRange("symbioticMaxDurationBonus", 0.25, 0.0, 10.0);
        sentientCostMultiplier = b
                .comment("Mana cost multiplier of the Sentient Wand.")
                .defineInRange("sentientCostMultiplier", 0.80, 0.1, 1.0);
        sentientDurationMultiplier = b
                .comment("Spell duration multiplier of the Sentient Wand.")
                .defineInRange("sentientDurationMultiplier", 1.30, 1.0, 10.0);
        sentientPotencyMultiplier = b
                .comment("Spell potency multiplier of the Sentient Wand.")
                .defineInRange("sentientPotencyMultiplier", 1.10, 1.0, 10.0);
        b.pop();

        b.comment("Grafted armour: each piece grows with the damage its wearer absorbs, then evolves into Sentient armour.")
         .push("armor");
        armorEvolveAt = b
                .comment("Damage a single Grafted piece must absorb to evolve. Damage taken is split between",
                         "the Grafted pieces worn at the time.")
                .defineInRange("evolveAt", 1500, 1, 10_000_000);
        graftedCostReductionPerPiece = b
                .comment("Mana cost reduction per worn Grafted piece. 0.03 = 3%. Applies to every spell the",
                         "wearer casts. The armour's total cost reduction never goes below a 0.5 multiplier.",
                         "Not a Redux attribute on purpose: Redux 0.8.9 re-applies attributes on every tick of a",
                         "channelled spell, so an attribute discount compounds towards free channelling.")
                .defineInRange("graftedCostReductionPerPiece", 0.03, 0.0, 0.25);
        sentientCostReductionPerPiece = b
                .comment("Mana cost reduction per worn Sentient piece.")
                .defineInRange("sentientCostReductionPerPiece", 0.05, 0.0, 0.25);
        sentientPotencyPerPiece = b
                .comment("Spell potency bonus per worn Sentient piece.")
                .defineInRange("sentientPotencyPerPiece", 0.03, 0.0, 0.25);
        lastStandEnabled = b
                .comment("Last Stand: with all four armour slots Grafted or Sentient (any mix), a death is",
                         "cancelled like a Totem of Undying, then the set goes on cooldown. Damage that",
                         "bypasses invulnerability (/kill, the void) is never stopped.")
                .define("lastStandEnabled", true);
        lastStandHealth = b
                .comment("Health the wearer is left with.")
                .defineInRange("lastStandHealth", 3.0, 0.5, 1024.0);
        lastStandImmunityTicks = b
                .comment("Ticks of full damage immunity right after Last Stand. 10 = half a second.")
                .defineInRange("lastStandImmunityTicks", 10, 0, 200);
        lastStandCleanseTicks = b
                .comment("Ticks of the Cleansing effect granted by Last Stand.")
                .defineInRange("lastStandCleanseTicks", 40, 0, 1200);
        lastStandCooldownSeconds = b
                .comment("Cooldown in game seconds (20 ticks each). Does not run while the server is off.")
                .defineInRange("lastStandCooldownSeconds", 90, 0, 86_400);
        b.pop();

        b.comment("Spell behaviour that is not a number in the spell's JSON file.")
         .push("spells");
        spineVolleyPuddleEvery = b
                .comment("Every Nth Spine Volley cast from the same wand leaves a poison cloud. 0 disables.")
                .defineInRange("spineVolleyPuddleEvery", 4, 0, 100);
        cleanseTickInterval = b
                .comment("How often, in ticks, the Cleansing effect strips harmful effects.")
                .defineInRange("cleanseTickInterval", 10, 1, 200);
        b.pop();

        b.comment("Infected Wizard (needs Fungal Infection: Spore). A Redux wizard that dies infected with",
                  "Mycelium rises as one. Its spell tier grows with the number of Proto Hiveminds in the world.")
         .push("infectedWizard");
        infectedSpellCount = b
                .comment("Spells of the wizard's element it knows, on top of Spine Volley.")
                .defineInRange("spellCount", 3, 0, 10);
        infectedAlwaysSpineVolley = b
                .comment("Every infected wizard also knows Spine Volley.")
                .define("alwaysSpineVolley", true);
        infectedWandDropChance = b
                .comment("Chance the wizard drops the Redux wand it carries.")
                .defineInRange("wandDropChance", 0.05, 0.0, 1.0);
        infectedEvolutionEnabled = b
                .comment("The wizard evolves like Spore's basic infected: after a kill and Spore's evolution timer",
                         "(Spore config, section Evolutions) it turns into one of the types below.")
                .define("evolutionEnabled", true);
        infectedEvolveChance = b
                .comment("Chance to evolve into a type from the list; otherwise a Scamper, as in Spore.")
                .defineInRange("evolveChance", 0.9, 0.0, 1.0);
        infectedEvolutions = b
                .comment("Entity types the wizard can evolve into. Spore's evolved forms work too (e.g. spore:knight).",
                         "Unknown ids are skipped with one log warning; an empty list always gives a Scamper.")
                .defineListAllowEmpty("evolutions", List.of("ebreduxaddon:mycomancer"), Config::isString);
        mycomancerWardRadius = b
                .comment("Mycomancer: radius of the Mycelial Ward that grants Resistance to nearby infected.")
                .defineInRange("wardRadius", 12.0, 0.0, 64.0);
        mycomancerWardInterval = b
                .comment("Ticks between two wards. Only while the Mycomancer has a target.")
                .defineInRange("wardInterval", 200, 20, 12_000);
        mycomancerWardDuration = b
                .comment("Ticks of Resistance granted by one ward.")
                .defineInRange("wardDuration", 120, 1, 12_000);
        mycomancerWardAmplifier = b
                .comment("Resistance level minus one (0 = Resistance I).")
                .defineInRange("wardAmplifier", 0, 0, 4);
        b.pop();

        b.comment("Fungal Infection: Spore. Only identifiers are used, so these work with any Spore 2.x and",
                  "do nothing when Spore is absent. Unknown ids are skipped with a single log warning.")
         .push("spore");
        sporeEnabled = b
                .comment("Master switch for everything in this section.")
                .define("enabled", true);
        sporeCleansedEffects = b
                .comment("Effects removed by Cleanse and by the Sentient set bonus, on top of every harmful effect.")
                .defineListAllowEmpty("cleansedEffects", List.of(
                        "spore:mycelium_ef", "spore:marker", "spore:corrosion", "spore:uneasy",
                        "spore:madness", "spore:frostbite", "spore:biled",
                        "spore:starvation"), Config::isString);
        sporePurifiedBlocks = b
                .comment("Blocks Purifying Pulse turns back, as \"source|target\" - the same format as Spore's own",
                         "conversion list. Blocks not listed are left alone. Spawners, laboratory blocks, hive",
                         "spawns and biomass are deliberately not listed: they belong to Spore's structures.")
                .defineListAllowEmpty("purifiedBlocks", List.of(
                        "spore:infested_dirt|minecraft:dirt",
                        "spore:infested_stone|minecraft:stone",
                        "spore:infested_netherrack|minecraft:netherrack",
                        "spore:infested_soul_sand|minecraft:soul_sand",
                        "spore:infested_end_stone|minecraft:end_stone",
                        "spore:infested_sand|minecraft:sand",
                        "spore:infested_gravel|minecraft:gravel",
                        "spore:infested_deepslate|minecraft:deepslate",
                        "spore:infested_red_sand|minecraft:red_sand",
                        "spore:infested_clay|minecraft:clay",
                        "spore:infested_cobblestone|minecraft:cobblestone",
                        "spore:infested_cobbled_deepslate|minecraft:cobbled_deepslate",
                        "minecraft:mycelium|minecraft:grass_block",
                        "spore:growths_big|minecraft:air",
                        "spore:growths_small|minecraft:air",
                        "spore:blomfung|minecraft:air",
                        "spore:bloomfung2|minecraft:air",
                        "spore:growth_mycelium|minecraft:air",
                        "spore:fungal_roots|minecraft:air",
                        "spore:wall_growths|minecraft:air",
                        "spore:wall_growths_big|minecraft:air",
                        "spore:mycelium_veins|minecraft:air",
                        "spore:fungal_stem|minecraft:air",
                        "spore:fungal_stem_top|minecraft:air",
                        "spore:hanging_fungal_stem|minecraft:air",
                        "spore:fungal_stem_sapling|minecraft:air",
                        "spore:underwater_fungal_stem_top|minecraft:water"), Config::isString);
        b.pop();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
