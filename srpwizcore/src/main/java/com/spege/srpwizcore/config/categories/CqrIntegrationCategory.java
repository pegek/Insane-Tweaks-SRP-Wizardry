package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * CQR &times; Spartan Weaponry integration (2026-08-01): dungeon mobs get Spartan gear and
 * working crossbows.
 *
 * <p>Two parts, both in {@code mixins.srpwizcore.*} configs without an
 * {@code IMixinConfigPlugin} — so none of these flags gates mixin <i>application</i>; they
 * are read live inside the handlers.
 *
 * <ul>
 * <li><b>Gear swap</b> — {@code MixinCqrSpawnerGearSwap} hooks
 * {@code TileEntitySpawner.spawnEntityFromNBT} (fires ONLY for CQR dungeon spawns; bosses go
 * through the separate {@code TileEntityBoss} and are never touched) and replaces vanilla
 * swords/axes/bows with weighted Spartan weapons of the same material tier. Port of the
 * proven {@code groovy/postInit/cqr_gear.groovy} v2 tables (instance pack).</li>
 * <li><b>Crossbow AI</b> — {@code MixinSwItemCrossbow} implements CQR's {@code IRangedWeapon}
 * on Spartan's crossbow, so the ranged AI task already present in every CQR mob picks it up
 * and shoots bolts (the item's own use-path is hard-gated on {@code EntityPlayer}).</li>
 * </ul>
 */
public class CqrIntegrationCategory {

    @Config.Comment({
            "Give Chocolate Quest Repoured dungeon mobs Spartan Weaponry gear instead of plain",
            "vanilla swords, axes and bows - a weapon of the same material tier, picked at random",
            "from a list that suits the mob (pirates get sabers, and so on).",
            "Only affects mobs placed by CQR dungeon spawners. Bosses and CQR's own custom weapons",
            "are never touched.",
            "This changes how dungeons play, so it is off by default. Needs Chocolate Quest Repoured",
            "and Spartan Weaponry. No restart needed. Default OFF."
    })
    @Config.Name("Gear Swap Enabled")
    public boolean gearSwapEnabled = false;

    @Config.Comment({
            "How often a matching vanilla weapon is actually swapped, in percent. 100 = always."
    })
    @Config.Name("Gear Swap: Swap Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapPct = 100;

    @Config.Comment({
            "Chance in percent that a swapped weapon also gets a random enchantment on top.",
            "Enchantments the original weapon already had are always kept regardless."
    })
    @Config.Name("Gear Swap: Extra Enchant Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapEnchantPct = 15;

    @Config.Comment({
            "Chance in percent that a melee weapon uses one of Spartan's alternative materials of the",
            "same strength (stone becomes copper or tin, iron becomes bronze, silver or steel)."
    })
    @Config.Name("Gear Swap: Exotic Material Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapExoticPct = 20;

    @Config.Comment({
            "Chance in percent that a bow becomes a crossbow rather than a longbow.",
            "Crossbows only work on mobs while 'Crossbow AI' below is active. 0 = longbows only."
    })
    @Config.Name("Gear Swap: Crossbow Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapCrossbowPct = 25;

    @Config.Comment({
            "How long a mob waits between crossbow shots, in ticks (20 = 1 second).",
            "Crossbows hit harder than bows, so they are meant to fire more slowly."
    })
    @Config.Name("Crossbow AI: Cooldown Ticks")
    @Config.RangeInt(min = 5, max = 400)
    public int crossbowCooldown = 50;

    @Config.Comment({
            "How long a mob visibly winds up a crossbow before firing, in ticks. 0 = fires instantly.",
            "Values above 18 are treated as 18 - past that the crossbow finishes loading and the mob",
            "would never actually shoot."
    })
    @Config.Name("Crossbow AI: Charge Ticks")
    @Config.RangeInt(min = 0, max = 200)
    public int crossbowChargeTicks = 25;

    @Config.Comment({
            "Also replace a weapon held in the off hand by dual-wielding dungeon mobs (pirates and",
            "the like). It becomes either a plain shield or a second Spartan weapon from the same",
            "list as the main hand. A shield handed out this way can still be lost to the two-handed",
            "rule below."
    })
    @Config.Name("Gear Swap: Offhand Swap Enabled")
    public boolean offhandSwapEnabled = true;

    @Config.Comment({
            "Chance in percent that the off-hand weapon becomes a shield rather than a second weapon."
    })
    @Config.Name("Gear Swap: Offhand Shield Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int offhandShieldPct = 20;

    @Config.Comment({
            "Chance in percent that a mob given a two-handed weapon (greatsword, longsword, pike and",
            "so on) loses its shield, the way a player would. A mob that loses its shield gets an",
            "extra healing potion in exchange. 0 = shields are always kept."
    })
    @Config.Name("Gear Swap: Two-Handed Shield Loss Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int twoHandedShieldLossPct = 50;

    @Config.Comment({
            "Turn CQR's staff-carrying mobs into real spellcasters by giving them an Electroblob's",
            "Wizardry wand with spells bound to it, matched to the staff they carried. CQR's own",
            "casting AI takes it from there.",
            "Staff guns are left alone. Healers give up their healing potions, since their heal spell",
            "replaces them.",
            "Changes how those mobs fight, so it is off by default. Needs Electroblob's Wizardry.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Staff Swap Enabled")
    public boolean staffSwapEnabled = false;

    @Config.Comment({
            "How many spells each converted mob gets on its wand. Its signature spell (a healer's",
            "heal, for instance) counts towards this. The mob cycles randomly through whichever are",
            "off cooldown."
    })
    @Config.Name("Staff Swap: Spells Per Mob")
    @Config.RangeInt(min = 1, max = 4)
    public int staffSpellsPerMob = 2;

    @Config.Comment({
            "Which wand tier converted mobs get: novice, apprentice, advanced or master.",
            "Anything unrecognised falls back to apprentice."
    })
    @Config.Name("Staff Swap: Wand Tier")
    public String staffWandTier = "apprentice";

    @Config.Comment({
            "Let CQR mobs actually use the extra reach of long weapons - pikes, halberds, lances,",
            "spears. Without this they can carry a pike but still have to walk right up to you.",
            "Attack speed already works without any setting: a rapier swings fast and a halberd slow",
            "in a mob's hands too.",
            "Changes how those fights feel, so it is off by default. No restart needed. Default OFF."
    })
    @Config.Name("Reach AI Enabled")
    public boolean reachAiEnabled = false;

    @Config.Comment({
            "How much of a long weapon's reach advantage a mob gets, compared to what a player would",
            "get from the same weapon. 0.5 gives a pike or halberd mob about one extra block; 1.0",
            "gives it the full player bonus."
    })
    @Config.Name("Reach AI: Factor")
    @Config.RangeDouble(min = 0.0D, max = 2.0D)
    public double reachFactor = 0.5D;

    @Config.Comment({
            "A mob with a long weapon stops closing in once you are comfortably within its reach, and",
            "pokes at you from there instead of crowding you like every other mob.",
            "Turn this off if reach mobs look too hesitant in narrow dungeon corridors."
    })
    @Config.Name("Reach AI: Standoff")
    public boolean reachStandoff = true;

    @Config.Comment({
            "Lock the loot in a CQR boss room until the boss is dead - opening any chest nearby does",
            "nothing while a living boss is within the radius below.",
            "Changes how dungeons are looted, so it is off by default. No restart needed. Default OFF."
    })
    @Config.Name("Boss Chest Lock Enabled")
    public boolean bossChestLockEnabled = false;

    @Config.Comment({
            "How far, in blocks, the game looks for a living boss when you open a chest - and how far",
            "around a dying boss its chests get the bonus-loot roll below."
    })
    @Config.Name("Boss Chest Lock Radius")
    @Config.RangeInt(min = 8, max = 128)
    public int bossChestLockRadius = 50;

    @Config.Comment({
            "When a CQR boss dies, each chest in range has this percent chance of gaining one bonus",
            "enchanted book from the list below. Each chest can only ever roll once.",
            "0 = no bonus loot."
    })
    @Config.Name("Boss Loot: Book Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int bossLootBookPct = 3;

    @Config.Comment({
            "Which enchantments the bonus book can be, picked evenly. Ids the game does not recognise",
            "are skipped with a warning in the log.",
            "The defaults come from So Many Enchantments - replace them if you do not have it."
    })
    @Config.Name("Boss Loot: Enchant Pool")
    public String[] bossLootEnchants = {
            "somanyenchantments:supremesharpness",
            "somanyenchantments:supremesmite",
            "somanyenchantments:supremebaneofarthropods",
            "somanyenchantments:supremefireaspect",
            "somanyenchantments:supremeflame",
            "somanyenchantments:advancedprotection",
            "somanyenchantments:advancedsharpness",
            "somanyenchantments:advancedpower",
            "somanyenchantments:advancedmending",
            "somanyenchantments:advancedlooting"};

    @Config.Comment({
            "Chance in percent that any dungeon mob carries one extra healing potion, on top of what",
            "it would normally have."
    })
    @Config.Name("Gear Swap: Extra Potion Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int extraPotionPct = 15;

    @Config.Comment({
            "EXPERIMENTAL. A dungeon mob that cannot reach you slowly digs through blocks YOU placed,",
            "so walling yourself into a dungeon room stops being a way to win.",
            "It can only break blocks that were not part of the original dungeon, so the dungeon",
            "itself is never damaged. Needs CQR's dungeon protection active.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Siege Dig Enabled")
    public boolean siegeDigEnabled = false;

    @Config.Comment({
            "How long a mob takes to break one of your blocks, in ticks (20 = 1 second).",
            "Cracks show and hitting sounds play while it digs."
    })
    @Config.Name("Siege Dig: Ticks Per Block")
    @Config.RangeInt(min = 20, max = 2400)
    public int siegeDigTicksPerBlock = 160;

    @Config.Comment({
            "How far ahead, in blocks, a stuck mob looks for one of your blocks to dig through."
    })
    @Config.Name("Siege Dig: Range")
    @Config.RangeInt(min = 1, max = 4)
    public int siegeDigRange = 2;

    @Config.Comment({"Blocks broken this way drop as items, since they were yours to begin with."})
    @Config.Name("Siege Dig: Drops")
    public boolean siegeDigDrops = true;

    @Config.Comment({
            "Blocks tougher than this are never dug through. For scale: stone is 1.5, obsidian is 50.",
            "Chests and other blocks that hold items are never dug through whatever this is set to."
    })
    @Config.Name("Siege Dig: Max Hardness")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double siegeDigMaxHardness = 10.0D;

    @Config.Comment({
            "EXPERIMENTAL. Limit how many CQR mobs may go after the same player at once, so a dungeon",
            "room does not send forty mobs at one person.",
            "The limit is strict - a mob over it will not even fight back until a slot frees up, which",
            "some people find odd. Slots free up on death, despawn, or when a mob picks a new target.",
            "0 = no limit. No restart needed. Default 0."
    })
    @Config.Name("Attacker Cap")
    @Config.RangeInt(min = 0, max = 64)
    public int attackerCap = 0;

    @Config.Comment({
            "Log every gear swap and every crossbow shot. Useful for checking the swap tables are",
            "doing what you expect; far too noisy for normal play.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;
}
