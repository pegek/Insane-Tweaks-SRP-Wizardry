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
            "Master switch for the dungeon-mob gear swap (vanilla sword/axe/bow -> weighted",
            "Spartan weapon of the same material tier). CQR custom weapons and anything not in",
            "the vanilla weapon map are never touched; bosses spawn via TileEntityBoss and are",
            "outside this hook entirely. Read live (no restart)."
    })
    @Config.Name("Gear Swap Enabled")
    public boolean gearSwapEnabled = true;

    @Config.Comment({
            "Percent chance to swap a matched vanilla weapon. 100 = always (user decision",
            "2026-07-31: vanilla weapons are swapped ALWAYS). Read live."
    })
    @Config.Name("Gear Swap: Swap Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapPct = 100;

    @Config.Comment({
            "Percent chance the swapped weapon additionally receives a random enchantment",
            "(vanilla addRandomEnchantment, levels 5-15). Enchantments already present on the",
            "original weapon are always carried over via NBT copy. Read live."
    })
    @Config.Name("Gear Swap: Extra Enchant Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapEnchantPct = 15;

    @Config.Comment({
            "Percent chance a swapped melee weapon uses an exotic material of the same tier",
            "(stone -> copper/tin, iron -> bronze/silver/steel) instead of the base one.",
            "Read live."
    })
    @Config.Name("Gear Swap: Exotic Material Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapExoticPct = 20;

    @Config.Comment({
            "Percent chance a vanilla bow becomes a Spartan crossbow instead of a longbow.",
            "Crossbows only work on mobs with the crossbow-AI mixin active (spartanweaponry +",
            "cqrepoured both loaded). 0 = longbows only. Read live."
    })
    @Config.Name("Gear Swap: Crossbow Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int gearSwapCrossbowPct = 25;

    @Config.Comment({
            "Cooldown in ticks between crossbow shots for CQR mobs (CQR's own bow branch uses",
            "20/30/40 by difficulty; crossbows hit harder so they fire slower). Read live."
    })
    @Config.Name("Crossbow AI: Cooldown Ticks")
    @Config.RangeInt(min = 5, max = 400)
    public int crossbowCooldown = 50;

    @Config.Comment({
            "Charge-up ticks before each crossbow shot (CQR's setActiveHand phase). 0 = instant",
            "fire. HARD-CAPPED in code at Spartan's crossbowTicksToLoad-2 (= 18): an unloaded",
            "crossbow's max use duration IS its load time and the hand resets when it runs out,",
            "so any higher value would mean the mob never fires. Read live."
    })
    @Config.Name("Crossbow AI: Charge Ticks")
    @Config.RangeInt(min = 0, max = 200)
    public int crossbowChargeTicks = 25;

    @Config.Comment({
            "When a swapped mob ALSO holds a vanilla sword/axe in its OFFHAND (CQR dual-wield",
            "structure NBT, e.g. pirates with a diamond axe), swap that too: Offhand Shield",
            "Percent chance for a plain shield, otherwise a Spartan weapon from the same",
            "per-entity pool as the mainhand (pirates roll a second saber/rapier). A granted",
            "shield still goes through the two-handed shield-loss roll. Read live."
    })
    @Config.Name("Gear Swap: Offhand Swap Enabled")
    public boolean offhandSwapEnabled = true;

    @Config.Comment({
            "Percent chance the offhand vanilla weapon becomes a shield instead of a second",
            "Spartan weapon. Read live."
    })
    @Config.Name("Gear Swap: Offhand Shield Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int offhandShieldPct = 20;

    @Config.Comment({
            "When the swapped weapon is two-handed (Spartan property two_handed or versatile,",
            "e.g. greatsword, longsword, pike), percent chance the mob LOSES its shield as the",
            "off-hand penalty; a mob that loses the shield gets +1 CQR healing potion in",
            "compensation. 0 = shields always kept. Read live."
    })
    @Config.Name("Gear Swap: Two-Handed Shield Loss Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int twoHandedShieldLossPct = 50;

    @Config.Comment({
            "CQR staff -> EBW wand swap (needs ebwizardry loaded). Every mapped staff",
            "(healing/fire/thunder/poison/vampiric/wind/spider/base) becomes an elemental",
            "wand with spells bound in its NBT; CQR's own EBW-integration AI casts them.",
            "staff_gun is a firearm and is never touched. Healers additionally lose their",
            "default healing potions (heal_ally is their sustain). Read live."
    })
    @Config.Name("Staff Swap Enabled")
    public boolean staffSwapEnabled = true;

    @Config.Comment({
            "How many spells from the staff's pool each mob gets bound to its wand (the",
            "guaranteed spell of a pool - e.g. heal_ally for healers - counts toward this).",
            "CQR's cast AI randomly cycles all bound, off-cooldown spells. Read live."
    })
    @Config.Name("Staff Swap: Spells Per Mob")
    @Config.RangeInt(min = 1, max = 4)
    public int staffSpellsPerMob = 2;

    @Config.Comment({
            "Wand tier handed out for swapped staffs: novice, apprentice, advanced or",
            "master (unknown values fall back to apprentice). Read live."
    })
    @Config.Name("Staff Swap: Wand Tier")
    public String staffWandTier = "apprentice";

    @Config.Comment({
            "Reach-aware melee AI: CQR mobs holding a Spartan weapon with the reach property",
            "(pike, halberd, lance, spear...) get extra melee reach - CQR only honours its own",
            "spears natively. Attack speed needs no flag: CQR already defers to the held item's",
            "own attack-speed modifier (rapier fast, halberd slow). Read live."
    })
    @Config.Name("Reach AI Enabled")
    public boolean reachAiEnabled = true;

    @Config.Comment({
            "Fraction of the player's reach advantage granted to mobs. Spartan's reach",
            "magnitude is the player's TOTAL reach (6 or 7 vs the 5.0 baseline); the mob bonus",
            "is (magnitude - 5) * this factor, so at 0.5 a pike/halberd mob gains +1 block",
            "(user decision 2026-08-01: +1, not the player's full +2). Read live."
    })
    @Config.Name("Reach AI: Factor")
    @Config.RangeDouble(min = 0.0D, max = 2.0D)
    public double reachFactor = 0.5D;

    @Config.Comment({
            "Standoff: a mob whose weapon grants extra reach stops advancing while the target",
            "is comfortably inside that reach (0.5-block margin), poking from its reach",
            "advantage instead of hugging like every other mob. Disable if reach mobs look",
            "too hesitant in tight dungeon corridors. Read live."
    })
    @Config.Name("Reach AI: Standoff")
    public boolean reachStandoff = true;

    @Config.Comment({
            "Boss-room chest lock: right-clicking any loot container is cancelled while a",
            "living CQR boss (team.cqr...entity.boss.*) is within Lock Radius blocks. Purely",
            "event-driven - no NBT, no tick cost. Read live."
    })
    @Config.Name("Boss Chest Lock Enabled")
    public boolean bossChestLockEnabled = true;

    @Config.Comment({
            "Radius (blocks) of the boss search around a clicked container, and of the chest",
            "sweep on boss death for the bonus-loot roll. Read live."
    })
    @Config.Name("Boss Chest Lock Radius")
    @Config.RangeInt(min = 8, max = 128)
    public int bossChestLockRadius = 50;

    @Config.Comment({
            "On CQR boss death, every loot container within Lock Radius rolls this percent",
            "chance to receive one bonus enchanted book from the Boss Loot Enchants list",
            "(level I-II, clamped to the enchant's max). Each chest rolls once ever",
            "(persistent marker in the tile's ForgeData). 0 = disabled. Read live."
    })
    @Config.Name("Boss Loot: Book Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int bossLootBookPct = 3;

    @Config.Comment({
            "Enchant pool for the boss-chest bonus book (uniform pick). Unknown ids are",
            "skipped with a log warning. Read live."
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
            "Percent chance for EVERY spawned CQR dungeon mob to carry +1 healing potion",
            "(on top of its defaults and the two-handed compensation). Read live."
    })
    @Config.Name("Gear Swap: Extra Potion Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int extraPotionPct = 15;

    @Config.Comment({
            "EXPERIMENTAL siege digging: a CQR mob that cannot path to its player target",
            "slowly breaks PLAYER-PLACED blocks in its own dungeon. Exact, not heuristic:",
            "uses the ProtectedRegion per-block state grid (isBreakable = not an original",
            "dungeon block), so dungeon walls are never touched. Needs the CQR protection",
            "system active for that dungeon. Read live."
    })
    @Config.Name("Siege Dig Enabled")
    public boolean siegeDigEnabled = true;

    @Config.Comment({
            "Ticks a mob needs to break one player-placed block (20 = 1s). Crack overlay",
            "and hit sounds play while digging. Read live."
    })
    @Config.Name("Siege Dig: Ticks Per Block")
    @Config.RangeInt(min = 20, max = 2400)
    public int siegeDigTicksPerBlock = 160;

    @Config.Comment({
            "How far (blocks, toward the target, feet+eye level) a stuck mob looks for a",
            "player-placed block to dig. Read live."
    })
    @Config.Name("Siege Dig: Range")
    @Config.RangeInt(min = 1, max = 4)
    public int siegeDigRange = 2;

    @Config.Comment({"Dug blocks drop as items (they are the player's property). Read live."})
    @Config.Name("Siege Dig: Drops")
    public boolean siegeDigDrops = true;

    @Config.Comment({
            "Blocks harder than this are never dug (vanilla hardness: stone 1.5, obsidian 50).",
            "Blocks with tile entities (chests etc.) are never dug regardless. Read live."
    })
    @Config.Name("Siege Dig: Max Hardness")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double siegeDigMaxHardness = 10.0D;

    @Config.Comment({
            "EXPERIMENTAL: at most this many CQR mobs may target the same player at once",
            "(strict - the next mob won't even retaliate until a slot frees up; slots free",
            "on death, despawn or target change). 0 = no cap. Read live."
    })
    @Config.Name("Attacker Cap")
    @Config.RangeInt(min = 0, max = 64)
    public int attackerCap = 6;

    @Config.Comment({
            "Log every gear swap and crossbow shot (INFO, [srpwizcore] prefix). Counters are",
            "always kept; this only controls per-event spam. Read live."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;
}
