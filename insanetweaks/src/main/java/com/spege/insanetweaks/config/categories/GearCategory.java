package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Living / Sentient gear line: the two Spellblades, the two Aegis shields, the
 * four armour sets and the two wands, plus the advanced properties they carry and a per-item
 * availability switch.
 *
 * <p>Every option here defaults to the value the item had before this category existed, so a pack
 * that does not edit the config plays exactly as it did.
 *
 * <p>Two shapes are used deliberately. Values that are flat per tier (a Spellblade's base damage,
 * a shield's armour points) are absolute, because that is what a pack author wants to type. Values
 * that are derived from an item's evolution progress (the wands' bonuses, which grow with evolution
 * points; the Spellblade synergy bonus, which grows with kills) are MULTIPLIERS applied to the
 * computed number, so an item half-way through its progression keeps its place on that curve
 * instead of being snapped to a fixed value. That is what stops existing saves from lurching.
 *
 * <p>Sub-category {@code @Config.Name} values double as .cfg section keys.
 */
public class GearCategory {

    @Config.Name("spellblades")
    @Config.LangKey("config.insanetweaks.category.gear.spellblades")
    @Config.Comment("Living and Sentient Spellblade: melee damage and spell power.")
    public final Spellblades spellblades = new Spellblades();

    @Config.Name("aegis")
    @Config.LangKey("config.insanetweaks.category.gear.aegis")
    @Config.Comment("Living and Sentient Aegis shields: armour and toughness.")
    public final Aegis aegis = new Aegis();

    @Config.Name("armour")
    @Config.LangKey("config.insanetweaks.category.gear.armour")
    @Config.Comment("The four armour sets: armour, toughness and spell discount.")
    public final Armour armour = new Armour();

    @Config.Name("wands")
    @Config.LangKey("config.insanetweaks.category.gear.wands")
    @Config.Comment("Living and Sentient Wand: spell power and the bonuses that grow as the wand evolves.")
    public final Wands wands = new Wands();

    @Config.Name("properties")
    @Config.LangKey("config.insanetweaks.category.gear.properties")
    @Config.Comment("Switch individual advanced properties off across the whole mod.")
    public final Properties properties = new Properties();

    @Config.Name("availability")
    @Config.LangKey("config.insanetweaks.category.gear.availability")
    @Config.Comment("Take individual pieces of gear out of the pack without breaking existing worlds.")
    public final Availability availability = new Availability();

    // =====================================================================
    public static class Spellblades {

        @Config.Name("Living Spellblade Attack Damage")
        @Config.Comment({
                "Melee damage of the Living Spellblade before any upgrades.",
                "For reference, a diamond sword is 7. This weapon is meant to be a late-game reward,",
                "which is why it starts so much higher - lower it if the blade outclasses everything",
                "else in your pack.",
                "Requires a restart. Default 21." })
        @Config.RangeDouble(min = 0.0D, max = 2048.0D)
        @Config.RequiresMcRestart
        public double livingAttackDamage = 21.0D;

        @Config.Name("Sentient Spellblade Attack Damage")
        @Config.Comment({
                "Melee damage of the Sentient Spellblade before any upgrades - the evolved form of",
                "the Living Spellblade.",
                "Requires a restart. Default 22." })
        @Config.RangeDouble(min = 0.0D, max = 2048.0D)
        @Config.RequiresMcRestart
        public double sentientAttackDamage = 22.0D;

        @Config.Name("Out Of Mana Damage Multiplier")
        @Config.Comment({
                "What a Spellblade's melee damage drops to when its wielder has no mana left. This is",
                "the drawback that balances the high base damage: run dry and the blade goes blunt.",
                "1.0 removes the penalty entirely; 0.25 makes running out much more painful.",
                "Read live - no restart needed. Default 0.5 (half damage)." })
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double outOfManaDamageMultiplier = 0.5D;

        @Config.Name("Armour Set Spell Power Multiplier")
        @Config.Comment({
                "Scales the spell-power bonus a Spellblade gives while you wear a full matching armour",
                "set. That bonus reaches +70% - on the Living Spellblade it grows with the blade's",
                "kill count, on the Sentient it is always at full.",
                "This is a multiplier rather than a fixed number on purpose: at 0.5 a half-evolved",
                "blade keeps its place on the curve instead of jumping, so nobody's weapon changes",
                "character overnight. 0 removes the set bonus completely.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double synergyPotencyMultiplier = 1.0D;

        @Config.Name("Runeword Spell Power Multiplier")
        @Config.Comment({
                "An extra reduction applied to the set bonus above, but only for Runeword spells.",
                "Runewords already carry large built-in bonuses of their own, so the set bonus is",
                "halved for them to stop the two stacking into something absurd.",
                "1.0 lets Runewords have the full set bonus like any other spell.",
                "Read live - no restart needed. Default 0.5 (half)." })
        @Config.RangeDouble(min = 0.0D, max = 1.0D)
        public double runewordSynergyMultiplier = 0.5D;

        @Config.Name("Magic Damage Multiplier")
        @Config.Comment({
                "Scales the bonus magic damage a Spellblade adds. Needs Potion Core installed; does",
                "nothing without it. The bonus is up to 10% and, on the Living Spellblade, grows with",
                "the blade's kill count.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double magicDamageMultiplier = 1.0D;
    }

    // =====================================================================
    public static class Aegis {

        @Config.Name("Living Aegis Armour")
        @Config.Comment({
                "Armour points the Living Aegis gives while held. For reference, a full set of iron",
                "armour is 15 points.",
                "Read live - no restart needed. Default 2." })
        @Config.RangeDouble(min = 0.0D, max = 128.0D)
        public double livingArmor = 2.0D;

        @Config.Name("Living Aegis Armour Toughness")
        @Config.Comment({
                "Armour toughness the Living Aegis gives while held. Toughness reduces how much of",
                "your armour a very heavy hit is allowed to punch through.",
                "Read live - no restart needed. Default 2." })
        @Config.RangeDouble(min = 0.0D, max = 128.0D)
        public double livingToughness = 2.0D;

        @Config.Name("Sentient Aegis Armour")
        @Config.Comment({
                "Armour points the Sentient Aegis gives while held - the evolved shield.",
                "Read live - no restart needed. Default 3." })
        @Config.RangeDouble(min = 0.0D, max = 128.0D)
        public double sentientArmor = 3.0D;

        @Config.Name("Sentient Aegis Armour Toughness")
        @Config.Comment({
                "Armour toughness the Sentient Aegis gives while held.",
                "Read live - no restart needed. Default 3." })
        @Config.RangeDouble(min = 0.0D, max = 128.0D)
        public double sentientToughness = 3.0D;
    }

    // =====================================================================
    public static class Armour {

        @Config.Name("Living Warlock Armour Multiplier")
        @Config.Comment({
                "Scales every armour point on the Living Warlock set (the parasite mage robes).",
                "The set is 5 / 9 / 7 / 4 points at 1.0, for a total of 25 - a full set of diamond",
                "armour is 20. A multiplier is used rather than four separate numbers so the set keeps",
                "its shape; 0.8 brings it in line with diamond.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double livingWarlockArmorMultiplier = 1.0D;

        @Config.Name("Living Warlock Toughness Multiplier")
        @Config.Comment({
                "Scales the armour toughness on the Living Warlock set (2.5 per piece at 1.0).",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double livingWarlockToughnessMultiplier = 1.0D;

        @Config.Name("Sentient Warlock Armour Multiplier")
        @Config.Comment({
                "Scales every armour point on the Sentient Warlock set - the evolved robes.",
                "5 / 11 / 9 / 5 points at 1.0, for a total of 30.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double sentientWarlockArmorMultiplier = 1.0D;

        @Config.Name("Sentient Warlock Toughness Multiplier")
        @Config.Comment({
                "Scales the armour toughness on the Sentient Warlock set (3.5 per piece at 1.0).",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double sentientWarlockToughnessMultiplier = 1.0D;

        @Config.Name("Living Battlemage Armour Multiplier")
        @Config.Comment({
                "Scales every armour point on the Living Battlemage set.",
                "4 / 10 / 8 / 4 points at 1.0, for a total of 26.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double livingBattlemageArmorMultiplier = 1.0D;

        @Config.Name("Living Battlemage Toughness Multiplier")
        @Config.Comment({
                "Scales the armour toughness on the Living Battlemage set (3.0 per piece at 1.0).",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double livingBattlemageToughnessMultiplier = 1.0D;

        @Config.Name("Sentient Battlemage Armour Multiplier")
        @Config.Comment({
                "Scales every armour point on the Sentient Battlemage set - the heaviest of the four.",
                "5 / 12 / 10 / 5 points at 1.0, for a total of 32.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double sentientBattlemageArmorMultiplier = 1.0D;

        @Config.Name("Sentient Battlemage Toughness Multiplier")
        @Config.Comment({
                "Scales the armour toughness on the Sentient Battlemage set (4.0 per piece at 1.0).",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double sentientBattlemageToughnessMultiplier = 1.0D;

        @Config.Name("Spell Discount Multiplier")
        @Config.Comment({
                "Scales the discount every one of these armour pieces gives to spell cost, cast time",
                "and cooldown. Each piece is worth 1-3% depending on the set, and on the Living",
                "Warlock set it grows as the armour absorbs damage.",
                "0 removes the discount entirely; 2.0 doubles it. Because it is a multiplier, armour",
                "part-way through its adaptation keeps its place on that curve.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double spellDiscountMultiplier = 1.0D;
    }

    // =====================================================================
    public static class Wands {

        @Config.Name("Living Wand Spell Power Bonus")
        @Config.Comment({
                "Flat spell-power bonus of the Living Wand, as a fraction: 0.40 means +40% stronger",
                "spells. This one does not grow - it is the wand's tier, not its progress.",
                "Read live - no restart needed. Default 0.40." })
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double livingPotencyBonus = 0.40D;

        @Config.Name("Sentient Wand Spell Power Bonus")
        @Config.Comment({
                "Flat spell-power bonus of the Sentient Wand, as a fraction: 0.80 means +80%.",
                "This is the single biggest number on the wand line - lower it first if the wands",
                "outclass the rest of your pack.",
                "Read live - no restart needed. Default 0.80." })
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double sentientPotencyBonus = 0.80D;

        @Config.Name("Duration Multiplier")
        @Config.Comment({
                "Scales how much longer these wands make spells last. Worth +5% to +25% depending on",
                "how far the wand has evolved.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double durationMultiplier = 1.0D;

        @Config.Name("Minion Health Multiplier")
        @Config.Comment({
                "Scales the extra health these wands give to summoned minions. Worth +5% to +25%",
                "depending on how far the wand has evolved.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double minionHealthMultiplier = 1.0D;

        @Config.Name("Spell Discount Multiplier")
        @Config.Comment({
                "Scales the discount these wands give to spell cost, cast time and cooldown - up to",
                "20% off each, growing as the wand evolves.",
                "0 removes the discount entirely. Because it is a multiplier, a part-evolved wand keeps",
                "its place on the curve instead of jumping.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double spellDiscountMultiplier = 1.0D;

        @Config.Name("Magic Damage Multiplier")
        @Config.Comment({
                "Scales the bonus magic damage these wands add (5% on the Living Wand, 10% on the",
                "Sentient). Needs Potion Core installed; does nothing without it.",
                "Read live - no restart needed. Default 1.0 (unchanged)." })
        @Config.RangeDouble(min = 0.0D, max = 10.0D)
        public double magicDamageMultiplier = 1.0D;

        @Config.Name("Sentient Wand Extra Minion")
        @Config.Comment({
                "A fully evolved Sentient Wand lets you keep one more summoned minion alive than",
                "normal. Turn this OFF to remove that last reward and leave the minion cap alone.",
                "Read live - no restart needed. Default ON." })
        public boolean sentientExtraMinion = true;
    }

    // =====================================================================
    public static class Properties {

        @Config.Name("Ashen Legacy")
        @Config.Comment({
                "The property that stops a dropped item being destroyed by fire, lava, cactus or",
                "explosions. Carried by every piece of this gear, and grantable to other items with a",
                "Property Book.",
                "Turn this OFF and the property stops working everywhere and stops appearing in",
                "tooltips - including on items that were already given it by a book. Nothing is lost:",
                "turn it back on and those items have it again.",
                "Read live - no restart needed. Default ON." })
        public boolean ashenLegacy = true;

        @Config.Name("Grave Defiance")
        @Config.Comment({
                "The Warlock armour sets' property: wearing the full set can refuse a killing blow,",
                "pulling you back from death and clearing hostile effects.",
                "This is the strongest defensive property in the mod - the first thing to switch off",
                "for a harder pack.",
                "Read live - no restart needed. Default ON." })
        public boolean graveDefiance = true;

        @Config.Name("Veil of Stasis")
        @Config.Comment({
                "The Sentient Warlock set's property: a small flat reduction to all incoming damage",
                "(-1.0%, or -1.5% once the armour has absorbed enough punishment).",
                "Read live - no restart needed. Default ON." })
        public boolean veilOfStasis = true;

        @Config.Name("Fleshbound")
        @Config.Comment({
                "The property that grafts a weapon to its wielder so it cannot be dropped or knocked",
                "out of your hands. Granted by a Property Book.",
                "Turning this OFF does not consume or destroy any book or any weapon - the property",
                "simply stops applying until you turn it back on.",
                "Read live - no restart needed. Default ON." })
        public boolean fleshbound = true;

        @Config.Name("Arcane Sundering")
        @Config.Comment({
                "The Sentient Spellblade's reward for 1900 kills: part of every strike lands as true",
                "damage that ignores armour, resistance and absorption, and part is converted from",
                "physical to magic. The exact shares live in the 'arcaneSundering' category.",
                "Turn this OFF to leave the blade a purely physical weapon.",
                "Read live - no restart needed. Default ON." })
        public boolean arcaneSundering = true;
    }

    // =====================================================================
    public static class Availability {

        @Config.Name("Living Spellblade")
        @Config.Comment({
                "Whether the Living Spellblade is obtainable. Turn this OFF and it has no recipe and",
                "is hidden from the creative menu and from recipe viewers.",
                "It is NOT deleted. The item stays registered, so any that already exist in a world",
                "keep working and keep their kill count - switching this off can never corrupt a save,",
                "and switching it back on restores the recipe.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean livingSpellblade = true;

        @Config.Name("Sentient Spellblade")
        @Config.Comment({
                "Whether the Sentient Spellblade is obtainable by crafting. Turn this OFF and it has",
                "no recipe and is hidden from the creative menu.",
                "Note this does NOT block the evolution path: a Living Spellblade that reaches its",
                "kill threshold still becomes one. Disable the Living Spellblade too if you want the",
                "whole line gone from new play.",
                "The item stays registered either way, so existing blades keep working.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean sentientSpellblade = true;

        @Config.Name("Living Aegis")
        @Config.Comment({
                "Whether the Living Aegis shield is obtainable. No recipe and hidden from creative when",
                "OFF; the item stays registered, so existing shields keep working.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean livingAegis = true;

        @Config.Name("Sentient Aegis")
        @Config.Comment({
                "Whether the Sentient Aegis shield is obtainable by crafting. No recipe and hidden from",
                "creative when OFF; the item stays registered, and the evolution path still works.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean sentientAegis = true;

        @Config.Name("Living Warlock Set")
        @Config.Comment({
                "Whether the Living Warlock armour set (the parasite mage robes) is obtainable. No",
                "recipes and hidden from creative when OFF; the four pieces stay registered, so armour",
                "already in a world keeps working and keeps what it has adapted to.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean livingWarlockSet = true;

        @Config.Name("Sentient Warlock Set")
        @Config.Comment({
                "Whether the Sentient Warlock armour set is obtainable by crafting. No recipes and",
                "hidden from creative when OFF; the pieces stay registered and the evolution path from",
                "the Living set still works.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean sentientWarlockSet = true;

        @Config.Name("Living Battlemage Set")
        @Config.Comment({
                "Whether the Living Battlemage armour set is obtainable. No recipes and hidden from",
                "creative when OFF; the pieces stay registered.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean livingBattlemageSet = true;

        @Config.Name("Sentient Battlemage Set")
        @Config.Comment({
                "Whether the Sentient Battlemage armour set is obtainable by crafting. No recipes and",
                "hidden from creative when OFF; the pieces stay registered and the evolution path from",
                "the Living set still works.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean sentientBattlemageSet = true;

        @Config.Name("Living Wand")
        @Config.Comment({
                "Whether the Living Wand is obtainable. No recipe and hidden from creative when OFF;",
                "the item stays registered, so wands already in a world keep working and keep their",
                "evolution progress.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean livingWand = true;

        @Config.Name("Sentient Wand")
        @Config.Comment({
                "Whether the Sentient Wand is obtainable by crafting. No recipe and hidden from",
                "creative when OFF; the item stays registered and the evolution path still works.",
                "Requires a restart. Default ON." })
        @Config.RequiresMcRestart
        public boolean sentientWand = true;
    }
}
