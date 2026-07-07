package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ThrallCategory {

    @Config.Name("General")
    @Config.Comment("Slot cap, timers and shared thrall behaviour.")
    public final General general = new General();

    @Config.Name("Collecting")
    @Config.Comment("COLLECTING mode: toss 1-4 block items, the thrall teleport-searches around home and harvests matches.")
    public final Collecting collecting = new Collecting();

    @Config.Name("Porter")
    @Config.Comment("PORTER mode: item ferry between the owner and home chests.")
    public final Porter porter = new Porter();

    @Config.Name("Farming")
    @Config.Comment("FARMING mode: harvest, replant, bone-meal and re-till.")
    public final Farming farming = new Farming();

    @Config.Name("Labour")
    @Config.Comment("WOODCUTTING and MINESHAFT modes.")
    public final Labour labour = new Labour();

    /** Direction a Porter carries items. Read per cycle (no restart needed). */
    public enum PorterDirection { TO_HOME, FROM_HOME }

    public static class General {
        @Config.Comment({
                "How many real-time hours a Thrall works in WOODCUTTING or MINESHAFT mode before returning home.",
                "After this time the Thrall teleports to its home point (if set) or follows the player.",
                "Set to 0 to disable the timer (Thrall works indefinitely)." })
        @Config.Name("Thrall Work Duration (hours)")
        @Config.RangeInt(min = 0, max = 24)
        public int workDurationHours = 2;

        @Config.Comment({ "Maximum number of Thralls a single player can own at once.",
                "Slot 1 is always the primary; extra slots cost more mana per cast (handled by SpellSummonThrall)." })
        @Config.Name("Max Slots Per Player")
        @Config.RangeInt(min = 1, max = 5)
        public int maxSlotsPerPlayer = 1;

        @Config.Comment("Range (in blocks) for the Thrall's passive item pickup.")
        @Config.Name("Passive Pickup Range")
        @Config.RangeDouble(min = 1.0, max = 8.0)
        public double passivePickupRange = 2.5;

        @Config.Comment("Distance (in blocks) at which a Thrall in FOLLOW mode teleports to its owner.")
        @Config.Name("Follow Teleport Distance")
        @Config.RangeDouble(min = 6.0, max = 64.0)
        public double followTeleportDistance = 18.0;
    }

    public static class Collecting {
        @Config.Comment({ "Master toggle for the Collecting work mode. Player tosses 1-4 block-items at the thrall;",
                "the thrall locks them in as targets, then teleport-explores around the home point harvesting matches." })
        @Config.Name("Enable Collecting Mode")
        public boolean enableCollectingMode = true;

        @Config.Comment("How long a single Collecting session runs before the thrall returns home to deposit.")
        @Config.Name("Collecting: Session Duration (minutes)")
        @Config.RangeInt(min = 5, max = 480)
        public int collectingDurationMinutes = 120;

        @Config.Comment({ "Seconds to wait after the FIRST item is accepted before locking in the target list.",
                "Hitting the max-targets cap below also forces an immediate lock-in." })
        @Config.Name("Collecting: Item Pickup Window (seconds)")
        @Config.RangeInt(min = 3, max = 60)
        public int collectingItemPickupTimeoutSeconds = 12;

        @Config.Comment("Maximum distinct (block, metadata) targets the player can register per session.")
        @Config.Name("Collecting: Max Targets")
        @Config.RangeInt(min = 1, max = 8)
        public int collectingMaxTargets = 4;

        @Config.Comment("Inner radius of the random teleport ring (blocks from home). The session scans the thrall's current position first.")
        @Config.Name("Collecting: Min TP Distance")
        @Config.RangeInt(min = 8, max = 256)
        public int collectingMinTpDistance = 8;

        @Config.Comment("Outer radius of the random teleport ring (blocks from home).")
        @Config.Name("Collecting: Max TP Distance")
        @Config.RangeInt(min = 16, max = 1024)
        public int collectingMaxTpDistance = 150;

        @Config.Comment("Sphere scan radius around the thrall after each teleport.")
        @Config.Name("Collecting: Scan Radius (blocks)")
        @Config.RangeInt(min = 4, max = 16)
        public int collectingScanRadius = 8;

        @Config.Comment("Maximum same-type blocks harvested via vein-BFS from a single found cluster.")
        @Config.Name("Collecting: Vein BFS Cap")
        @Config.RangeInt(min = 1, max = 256)
        public int collectingVeinMaxBlocks = 50;

        @Config.Comment({ "Number of consecutive empty scans before aborting the session early.",
                "Prevents the thrall from spinning forever when targets are extinct in the area." })
        @Config.Name("Collecting: Max Empty Cycles")
        @Config.RangeInt(min = 5, max = 200)
        public int collectingMaxEmptyCycles = 30;

        @Config.Comment("Ticks between teleport-and-scan cycles (20 ticks = 1 second).")
        @Config.Name("Collecting: Tick Interval")
        @Config.RangeInt(min = 10, max = 200)
        public int collectingTickInterval = 40;

        @Config.Comment("Horizontal radius the thrall scans for chests when depositing collected items.")
        @Config.Name("Collecting: Chest Scan Radius (blocks)")
        @Config.RangeInt(min = 8, max = 64)
        public int collectingChestScanRange = 40;

        @Config.Comment({ "Minutes the locked target list stays valid after a player-issued mode interrupt.",
                "Re-clicking COLLECTING within this window resumes the session with remaining time. 0 = always restart." })
        @Config.Name("Collecting: Resume Window (minutes)")
        @Config.RangeInt(min = 0, max = 60)
        public int collectingResumeWindowMinutes = 5;
    }

    public static class Porter {
        @Config.Comment({ "Master toggle for the Porter work mode. If false, Thralls cannot enter PORTER mode.",
                "Porter mode acts as an auto-stocker: the Thrall periodically teleports to its owner,",
                "pulls items from the owner's inventory that already have a sample stored in chests near home,",
                "and ferries them back. Anchored to the home point." })
        @Config.Name("Enable Porter Mode")
        public boolean enablePorterMode = true;

        @Config.Comment({
                "Seconds between porter delivery cycles. Lower values are more responsive but cause more",
                "teleport noise/particles. Default is balanced for normal play." })
        @Config.Name("Porter: Cycle Interval (seconds)")
        @Config.RangeInt(min = 5, max = 300)
        public int porterIntervalSeconds = 30;

        @Config.Comment({
                "Porter carry direction, read fresh each cycle (no restart needed):",
                "  TO_HOME   — default. Pulls matching items from the owner's main inventory and stores them",
                "              in home chests (the classic auto-stocker).",
                "  FROM_HOME — reverse restock. Pulls from home chests ONLY item types the owner already",
                "              carries with non-full stacks, teleports to the owner and tops those stacks up.",
                "              Never introduces new item types and never touches hotbar/armour/offhand." })
        @Config.Name("Porter: Direction")
        public PorterDirection porterDirection = PorterDirection.TO_HOME;

        @Config.Comment({
                "Maximum distance (in blocks) the Porter will travel from its home to reach the owner.",
                "Beyond this range the Porter idles. Cross-dimension delivery is not supported." })
        @Config.Name("Porter: Max Range (blocks)")
        @Config.RangeInt(min = 16, max = 256)
        public int porterTeleportRange = 96;

        @Config.Comment({ "Horizontal radius (in blocks) the Porter scans for chests around its home.",
                "Used both for the manifest build and for depositing fetched items. Vertical scan is fixed at +/-4 blocks." })
        @Config.Name("Porter: Chest Scan Radius (blocks)")
        @Config.RangeInt(min = 8, max = 64)
        public int porterChestScanRange = 40;

        @Config.Comment({
                "If true, the Porter actively consolidates chest contents each cycle: items get moved into",
                "the chest where their type already has the most stacks, so types don't drift across chests over time.",
                "Bounded by a small per-cycle transfer cap to avoid stutter." })
        @Config.Name("Porter: Active Chest Sorting")
        public boolean enablePorterSorting = true;
    }

    public static class Farming {
        @Config.Comment("Master toggle for the Farming work mode. If false, Thralls cannot enter FARMING mode.")
        @Config.Name("Enable Farming Mode")
        public boolean enableFarmingMode = true;

        @Config.Comment({
                "Horizontal radius (in blocks) around the Thrall's home point that is scanned for mature crops.",
                "Vertical scan is fixed at ±2 blocks." })
        @Config.Name("Farming: Scan Radius")
        @Config.RangeInt(min = 4, max = 32)
        public int farmRadius = 12;

        @Config.Comment({
                "If true, Thralls in FARMING mode will use bone meal from their inventory on immature crops",
                "to accelerate growth. Bone meal is consumed per use." })
        @Config.Name("Farming: Use Bone Meal")
        public boolean farmUseBoneMeal = true;
    }

    public static class Labour {
        @Config.Comment("Master toggle for the Woodcutting work mode. If false, Thralls cannot enter WOODCUTTING mode.")
        @Config.Name("Enable Woodcutting Mode")
        public boolean enableWoodcuttingMode = true;

        @Config.Comment("Master toggle for the Mineshaft work mode. If false, Thralls cannot enter MINESHAFT mode.")
        @Config.Name("Enable Mineshaft Mode")
        public boolean enableMineshaftMode = true;

        @Config.Comment("Lowest Y level the spiral shaft will descend to before transitioning to strip mining.")
        @Config.Name("Mineshaft: Min Y")
        @Config.RangeInt(min = 1, max = 60)
        public int mineshaftDepthMin = 5;

        @Config.Comment("Length of the main strip-mine tunnel, in blocks.")
        @Config.Name("Mineshaft: Main Tunnel Length")
        @Config.RangeInt(min = 8, max = 200)
        public int mineshaftStripLength = 50;

        @Config.Comment("Distance between branch tunnels along the main corridor.")
        @Config.Name("Mineshaft: Branch Spacing")
        @Config.RangeInt(min = 2, max = 8)
        public int mineshaftBranchSpacing = 3;
    }
}
