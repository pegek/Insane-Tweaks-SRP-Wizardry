package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID, name = "insanetweaks", category = "")
public class ModConfig {

        @Config.Name("[ 1 ] MODULES & INTEGRATIONS")
        @Config.Comment("Toggle main systems and cross-mod integrations.")
        public static final Modules modules = new Modules();

        @Config.Name("[ 2 ] TWEAKS & FIXES")
        @Config.Comment("Configure specific bugfixes and mechanic alterations.")
        public static final Tweaks tweaks = new Tweaks();

        @Config.Name("[ 3 ] SKILL TRAITS")
        @Config.Comment("Configure SP costs, parent trees, and requirements for custom traits.")
        public static final Traits traits = new Traits();

        @Config.Name("[ 4 ] CLIENT & DEBUG")
        @Config.Comment("Visual toggles and debugging tools.")
        public static final Client client = new Client();

        @Config.Name("[ 5 ] TOMBSTONE TWEAKS")
        @Config.Comment("Configure specific bugfixes and mechanic alterations for Corail Tombstone.")
        public static final TombstoneTweaks tombstone = new TombstoneTweaks();

        @Config.Name("[ 6 ] THRALL")
        @Config.Comment("Configure the Thrall companion entity (slot caps, work modes, AI tunables).")
        public static final Thrall thrall = new Thrall();

        @Config.Name("[ 7 ] SIM WIZARD")
        @Config.Comment("Configure the assimilated sim_wizard parasite-mage entity (full SRP parasite, aggressive vs non-parasite).")
        public static final SimWizard simWizard = new SimWizard();

        // ========================================================================
        // CATEGORY 1: MODULES & INTEGRATIONS
        // ========================================================================
        public static class Modules {
                @Config.Comment("Enables the SRParasites and Electroblob's Wizardry Bridge. This includes the evolving Living Armor, Sentient Spellblades, and the Sentient Aegis shield.")
                @Config.Name("Enable SRP-EBWizardry Bridge")
                @Config.RequiresMcRestart
                public boolean enableSrpEbWizardryBridge = true;

                @Config.Comment("Enables the custom Reskillable trait module. Requires Reskillable. The mod preserves the legacy compatskills namespace for save compatibility and may update reskillable.cfg with an automatic backup.")
                @Config.Name("Enable Skills Module")
                @Config.RequiresMcRestart
                public boolean enableSkillsModule = false;

                @Config.Comment("Registers Cost, Potency and Speedcast Core items for use with Electroblob's Wizardry armor upgrades. Disable if you don't use EBWizardry.")
                @Config.Name("Enable Custom Cores (EBWizardry)")
                @Config.RequiresMcRestart
                public boolean enableCustomCores = true;

                @Config.Comment("Registers all InsaneTweaks custom Electroblob's Wizardry spells. Disable this to remove every spell added by the mod from the spell registry.")
                @Config.Name("Enable Spells")
                @Config.RequiresMcRestart
                public boolean enableSpells = true;

                @Config.Comment({
                                "Enables the Bauble Fruit system. Bauble Fruits are one-time consumable items that permanently",
                                "grant the player an additional bauble slot when eaten.",
                                "Requires BaublesEX (v2.0.0+) to function. Does nothing if only the original Baubles (v1.5.x) is installed." })
                @Config.Name("Enable Bauble Fruits")
                @Config.RequiresMcRestart
                public boolean enableBaubleFruits = true;
        }

        // ========================================================================
        // CATEGORY 2: TWEAKS & FIXES
        // ========================================================================
        public static class Tweaks {

                @Config.Comment({
                                "How many real-time hours a Thrall works in WOODCUTTING or MINESHAFT mode before returning home.",
                                "After this time the Thrall teleports to its home point (if set) or follows the player.",
                                "Set to 0 to disable the timer (Thrall works indefinitely)." })
                @Config.Name("Thrall Work Duration (hours)")
                @Config.RangeInt(min = 0, max = 24)
                public int thrallWorkDurationHours = 2;

                @Config.Comment({
                                "Prevents the Enigmatic Legacy 'Cursed Ring' from forcing summoned creatures (e.g. Fer Cow Minion) to attack their own caster.",
                                "When enabled, all Electroblob's Wizardry summoned creatures correctly report as being 'on the same team' as their owner,",
                                "which makes the Cursed Ring's anger loop skip them entirely.",
                                "Safe to leave enabled even if Enigmatic Legacy is not installed — the fix costs nothing when the ring is absent." })
                @Config.Name("Enable Cursed Ring Minion Fix")
                public boolean enableCursedRingFix = true;

                @Config.Comment({
                                "Additional effects removed by the CLEANSE effect, on top of all effects where isBeneficial() == false.",
                                "Add effect IDs here if a mod's negative effect is not getting removed automatically.",
                                "Example: minecraft:glowing" })
                @Config.Name("Cleanse Effect List")
                public String[] cleanseAdditionalEffects = { "srparasites:novision", "srparasites:prey",
                                "srparasites:viral" };
        }

        // ========================================================================
        // CATEGORY 3: SKILL TRAITS
        // ========================================================================
        public static class Traits {
                @Config.Name("Fast Learner")
                public TraitConfig fastLearner = new TraitConfig(6, "attack", new String[] { "reskillable:attack|8" });

                @Config.Name("Assimilated Warfare")
                public TraitConfig assimilatedWarfare = new TraitConfig(6, "attack",
                                new String[] { "reskillable:attack|18" });

                @Config.Name("Spider's Grace")
                public TraitConfig spidersGrace = new TraitConfig(7, "defense",
                                new String[] { "reskillable:defense|35" });

                @Config.Name("Iron Stomach")
                public TraitConfig ironStomach = new TraitConfig(5, "defense",
                                new String[] { "reskillable:defense|15" });

                @Config.Name("Double Loot")
                public TraitConfig doubleLoot = new TraitConfig(5, "gathering",
                                new String[] { "reskillable:gathering|10" });

                @Config.Name("Enchant Fishing")
                public TraitConfig enchantFishing = new TraitConfig(7, "gathering",
                                new String[] { "reskillable:gathering|32" });

                @Config.Name("Astral Prospector")
                public TraitConfig astralProspector = new TraitConfig(7, "mining",
                                new String[] { "reskillable:mining|30" });

                @Config.Name("Supreme Enchanter")
                public TraitConfig supremeEnchanter = new TraitConfig(8, "building",
                                new String[] { "reskillable:building|30" });

                @Config.Name("Bob the Builder")
                public TraitConfig bobTheBuilder = new TraitConfig(5, "building",
                                new String[] { "reskillable:building|18" });

                @Config.Name("Angry Farmer")
                public TraitConfig angryFarmer = new TraitConfig(10, "farming",
                                new String[] { "reskillable:farming|45" });

                @Config.Name("Adapted Vegetation")
                public TraitConfig adaptedVegetation = new TraitConfig(5, "farming",
                                new String[] { "reskillable:farming|18" });

                @Config.Name("Meditation")
                public TraitConfig meditation = new TraitConfig(6, "agility",
                                new String[] { "reskillable:agility|18", "reskillable:magic|10" });

                @Config.Name("Arcane Mastery")
                public TraitConfig arcaneMastery = new TraitConfig(5, "magic", new String[] { "reskillable:magic|18" });

                @Config.Name("School of Alteration")
                public TraitConfig schoolOfAlteration = new TraitConfig(5, "magic",
                                new String[] { "reskillable:magic|28" });

                @Config.Name("School of Conjuration")
                public TraitConfig schoolOfConjuration = new TraitConfig(5, "magic",
                                new String[] { "reskillable:magic|22" });

                @Config.Name("School of Destruction")
                public TraitConfig schoolOfDestruction = new TraitConfig(5, "magic",
                                new String[] { "reskillable:magic|22" });

                @Config.Name("Archmage")
                public TraitConfig archmage = new TraitConfig(8, "magic", new String[] { "reskillable:magic|45" });
        }

        // ========================================================================
        // TRAIT CONFIG HELPER
        // ========================================================================
        public static class TraitConfig {
                @Config.Name("SP Cost")
                @Config.Comment("Skill point cost to unlock this trait.")
                @Config.RequiresMcRestart
                public int cost;

                @Config.Name("Parent Skill Tree")
                @Config.Comment("The Reskillable skill tree this trait belongs to (e.g. magic, attack, defense).")
                @Config.RequiresMcRestart
                public String parentSkill;

                @Config.Name("Requirements")
                @Config.Comment("List of required skills and levels. Format: reskillable:skill_name|level")
                @Config.RequiresMcRestart
                public String[] requirements;

                public TraitConfig(int cost, String parentSkill, String[] requirements) {
                        this.cost = cost;
                        this.parentSkill = parentSkill;
                        this.requirements = requirements;
                }
        }

        // ========================================================================
        // CATEGORY 4: CLIENT & DEBUG
        // ========================================================================
        public static class Client {
                @Config.Comment({ "Hides the CLEANSE effect icon and text from the HUD and inventory effect panel.",
                                "Enable this flag to suppress the effect from appearing in the GUI entirely.",
                                "The mechanic itself still works regardless of this setting.",
                                "Intended as a temporary toggle while the effect's visuals are being refined." })
                @Config.Name("Hide Cleanse Effect from GUI")
                public boolean hideCleanseHudEffect = false;

                @Config.Comment("Should mod display info whether he found any cursed items in players inventory on death")
                @Config.Name("Display info on death")
                public boolean displayInfoOnDeath = false;

                @Config.Comment("Whether to display debug info for various mod mechanics.")
                @Config.Name("Display DEBUG INFO")
                public boolean displayDebugInfo = false;

                @Config.Comment({
                                "Enables verbose per-tick debug logging for Thrall AI tasks (woodcutting, mineshaft, navigation, deposit).",
                                "Useful for diagnosing AI behavior. WARN-level entries are always logged regardless of this flag." })
                @Config.Name("Thrall AI Debug Logs")
                public boolean enableThrallDebugLogs = false;

                @Config.Comment({
                                "If true, suppresses the mod-recommendation and version-warning messages that appear in chat",
                                "when you join a world (e.g. missing optional mods, SRPextra version hints).",
                                "Warnings are still written to the log file regardless of this setting."
                })
                @Config.Name("Suppress Startup Chat Warnings")
                public boolean suppressStartupWarningsInChat = false;
        }

        // ========================================================================
        // CATEGORY 5: TOMBSTONE TWEAKS
        // ========================================================================
        public static class TombstoneTweaks {
                @Config.Comment("Master switch for all Corail Tombstone tweaks. If false, no Tombstone related tweaks will be applied.")
                @Config.Name("Enable Tombstone Tweaks")
                @Config.RequiresMcRestart
                public boolean enableTombstoneTweaks = false;

                @Config.Comment({
                                "Fixes the Curse of Possession exploit with Corail Tombstone.",
                                "Cursed items will now properly vanish on death instead of hiding in your grave.",
                                "Use /itweaks restore cursed <player> to view/restore backups."
                })
                @Config.Name("Enable Curse of Possession Patch")
                public boolean enableCurseOfPossessionPatch = false;

                @Config.Comment("Removes the vanilla Tombstone recipe to craft an Enchanted Grave Key using an Ender Pearl.")
                @Config.Name("Disable Enchant Key Recipe")
                @Config.RequiresMcRestart
                public boolean disableEnchantKeyRecipe = true;

                // ----------------------------------------------------------------
                // GRAVE ITEM DECAY
                // ----------------------------------------------------------------
                @Config.Comment({
                                "Enable grave item decay \u2014 after despawn delay, grave loses 1 random stack every interval.",
                                "Use /itweaks restore decay <player> to view/restore items lost to grave decay."
                })
                @Config.Name("Enable Grave Item Decay")
                public boolean enableGraveItemDecay = false;

                @Config.Comment("Ticks before decay starts. Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
                @Config.Name("Grave Decay Start Delay (ticks)")
                @Config.RangeInt(min = 0, max = 192000)
                public int graveDecayStartTicks = 6000;

                @Config.Comment("Interval in ticks between each item removal. 1200 = 60 seconds.")
                @Config.Name("Grave Decay Interval (ticks)")
                @Config.RangeInt(min = 20, max = 24000)
                public int graveDecayIntervalTicks = 1200;

                @Config.Comment("Max number of decay snapshots kept per player. Oldest entries are removed when exceeded.")
                @Config.Name("Max Grave Decay Backup History")
                @Config.RangeInt(min = 1, max = 50)
                public int graveDecayMaxHistory = 10;

                // ----------------------------------------------------------------
                // MECHANICS NERFS
                // ----------------------------------------------------------------
                @Config.Comment("Reduces the chance for grave_dust to drop from undead mobs.")
                @Config.Name("Nerf Grave Dust Drop Rate")
                public boolean nerfGraveDustDrop = false;

                @Config.Comment("The percentage chance (0-100) for a grave_dust drop to be kept. 100 = native rate(10%), 0 = never drops.")
                @Config.Name("Grave Dust Drop Chance (%)")
                @Config.RangeInt(min = 0, max = 100)
                public int graveDustDropChance = 100;

                @Config.Comment("Cooldown in hours before the Book of Disenchantment can be used again. Set to 0 to disable cooldown.")
                @Config.Name("Book of Disenchantment Cooldown (Hours)")
                @Config.RangeDouble(min = 0, max = 720)
                public double bookOfDisenchantmentCooldownConfig = 0.1;

                @Config.Comment("Cooldown in hours before the Book of Magic Impregnation can be used again. Set to 0 to disable cooldown.")
                @Config.Name("Book of Magic Impregnation Cooldown (Hours)")
                @Config.RangeDouble(min = 0, max = 720)
                public double bookOfMagicImpregnationCooldownConfig = 0.1;

                // ----------------------------------------------------------------
                // KNOWLEDGE OF DEATH - PERK SETTINGS
                // Each perk has: enabled (bool) and maxLevel (int capped at native max)
                // Native max levels: alchemist=5, concentration=2, gladiator=5,
                // jailer=5, memento_mori=dynamic, rune_inscriber=5, scribe=5,
                // shadow_walker=5, treasure_seeker=5, witch_doctor=5
                // ----------------------------------------------------------------
                @Config.Name("Perk: Alchemist")
                @Config.Comment("Controls the Alchemist perk (scroll duration bonus). Native max level: 5")
                public PerkConfig alchemist = new PerkConfig(true, 5);

                @Config.Name("Perk: Concentration")
                @Config.Comment("Controls the Concentration perk (soul gathering bonus). Native max level: 2")
                public PerkConfig concentration = new PerkConfig(true, 2);

                @Config.Name("Perk: Gladiator")
                @Config.Comment("Controls the Gladiator perk (combat bonuses). Native max level: 5")
                public PerkConfig gladiator = new PerkConfig(true, 5);

                @Config.Name("Perk: Jailer")
                @Config.Comment("Controls the Jailer perk (enchanted grave key chance). Native max level: 5 (dynamic, depends on Tombstone's chanceEnchantedGraveKey config)")
                public PerkConfig jailer = new PerkConfig(true, 5);

                @Config.Name("Perk: Memento Mori")
                @Config.Comment("Controls the Memento Mori perk (XP loss reduction). Native max level is dynamic (depends on xpLoss config). Set maxLevel to 0 to equivalent disable.")
                public PerkConfig mementoMori = new PerkConfig(true, 5);

                @Config.Name("Perk: Rune Inscriber")
                @Config.Comment("Controls the Rune Inscriber perk (tablet cooldown reduction). Native max level: 5")
                public PerkConfig runeInscriber = new PerkConfig(true, 5);

                @Config.Name("Perk: Scribe")
                @Config.Comment("Controls the Scribe perk (book of disenchantment bonus uses). Native max level: 5")
                public PerkConfig scribe = new PerkConfig(true, 5);

                @Config.Name("Perk: Shadow Walker")
                @Config.Comment("Controls the Shadow Walker perk (ghostly shape efficiency). Native max level: 5")
                public PerkConfig shadowWalker = new PerkConfig(true, 5);

                @Config.Name("Perk: Treasure Seeker")
                @Config.Comment("Controls the Treasure Seeker perk (grave loot bonuses). Native max level: 5")
                public PerkConfig treasureSeeker = new PerkConfig(true, 5);

                @Config.Name("Perk: Witch Doctor")
                @Config.Comment("Controls the Witch Doctor perk (voodoo poppet efficiency). Native max level: 5")
                public PerkConfig witchDoctor = new PerkConfig(true, 5);
        }

        // ========================================================================
        // CATEGORY 6: THRALL
        // ========================================================================
        public static class Thrall {
                @Config.Comment({ "Maximum number of Thralls a single player can own at once.",
                                "Slot 1 is always the primary; extra slots cost more mana per cast (handled by SpellSummonThrall)." })
                @Config.Name("Max Slots Per Player")
                @Config.RangeInt(min = 1, max = 5)
                public int maxSlotsPerPlayer = 1;

                @Config.Comment("Master toggle for the Mineshaft work mode. If false, Thralls cannot enter MINESHAFT mode.")
                @Config.Name("Enable Mineshaft Mode")
                public boolean enableMineshaftMode = true;

                @Config.Comment("Master toggle for the Woodcutting work mode. If false, Thralls cannot enter WOODCUTTING mode.")
                @Config.Name("Enable Woodcutting Mode")
                public boolean enableWoodcuttingMode = true;

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

                /** Direction a Porter carries items. Read per cycle (no restart needed). */
                public enum PorterDirection { TO_HOME, FROM_HOME }

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

                @Config.Comment({ "Master toggle for the Collecting work mode. Player tosses 1-4 block-items at the thrall;",
                                "the thrall locks them in as targets, then teleport-explores around the home point harvesting matches." })
                @Config.Name("Enable Collecting Mode")
                @Config.RequiresMcRestart
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

                @Config.Comment({ "Inner radius of the random teleport ring (blocks from home).",
                                "Default lowered to 8 so a collecting session searches close to home before",
                                "ranging out (C-2b). Range bounds unchanged." })
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

                @Config.Comment("Range (in blocks) for the Thrall's passive item pickup.")
                @Config.Name("Passive Pickup Range")
                @Config.RangeDouble(min = 1.0, max = 8.0)
                public double passivePickupRange = 2.5;

                @Config.Comment("Distance (in blocks) at which a Thrall in FOLLOW mode teleports to its owner.")
                @Config.Name("Follow Teleport Distance")
                @Config.RangeDouble(min = 6.0, max = 64.0)
                public double followTeleportDistance = 18.0;
        }

        // ========================================================================
        // CATEGORY 7: SIM WIZARD
        // ========================================================================
        public static class SimWizard {
                @Config.Comment({
                                "Master toggle for the sim_wizard entity.",
                                "When false, the SrpWizardryAssimilationHelper bridge falls back to srparasites:sim_human",
                                "for both ebwizardry:wizard and ebwizardry:evil_wizard conversions."
                })
                @Config.Name("Enable Sim Wizard")
                @Config.RequiresMcRestart
                public boolean enabled = true;

                @Config.Comment({
                                "Multiplier applied on top of EntityInfHuman base health (SRPAttributes.INFHUMAN_HEALTH).",
                                "v2.1: nerfed from 1.30 -> 1.15 (sim_wizard was overall too tanky in playtest)."
                })
                @Config.Name("Health Multiplier")
                @Config.RangeDouble(min = 0.5, max = 5.0)
                public double healthMultiplier = 1.15D;

                @Config.Comment({
                                "Flat extra HP added on top of the multiplied base health.",
                                "v2.1: nerfed from 5.0 -> 2.5."
                })
                @Config.Name("Extra Health (flat)")
                @Config.RangeDouble(min = 0.0, max = 50.0)
                public double extraHealth = 2.5D;

                @Config.Comment({
                                "Multiplier applied on top of EntityInfHuman base armor.",
                                "v2.1: nerfed from 1.45 -> 0.70 (wizard fantasy = squishy caster, not armored bruiser).",
                                "Values < 1.0 reduce armor below base."
                })
                @Config.Name("Armor Multiplier")
                @Config.RangeDouble(min = 0.0, max = 5.0)
                public double armorMultiplier = 0.70D;

                @Config.Comment({
                                "Multiplier applied on top of EntityInfHuman base movement speed.",
                                "v3.2: reduced to 1.0 (baseline inf_human speed) - the wizard is a caster that kites,",
                                "not a chaser. Was 1.15 in v2.1-v3.1."
                })
                @Config.Name("Movement Speed Multiplier")
                @Config.RangeDouble(min = 0.5, max = 3.0)
                public double speedMultiplier = 1.0D;

                @Config.Comment("Minimum follow / aggro range (blocks). Forced when SRP infectedFollow is lower.")
                @Config.Name("Min Follow Range")
                @Config.RangeInt(min = 8, max = 64)
                public int minFollowRange = 32;

                @Config.Comment("Maximum spell-cast decision range (blocks). Beyond this distance the wizard reverts to navigation/melee.")
                @Config.Name("Spell Decision Range")
                @Config.RangeInt(min = 4, max = 64)
                public int decisionRange = 24;

                @Config.Comment({
                                "Spell potency multiplier passed via SpellModifiers.POTENCY.",
                                "v2.1: nerfed from 1.35 -> 1.175 (bonus halved from +35% to +17.5%)."
                })
                @Config.Name("Spell Potency Multiplier")
                @Config.RangeDouble(min = 1.0, max = 3.0)
                public double potencyMultiplier = 1.175D;

                @Config.Comment({
                                "Spell range multiplier passed via SpellModifiers \"range\" key.",
                                "v2.1: nerfed from 1.35 -> 1.175 (bonus halved from +35% to +17.5%)."
                })
                @Config.Name("Spell Range Multiplier")
                @Config.RangeDouble(min = 1.0, max = 3.0)
                public double rangeMultiplier = 1.175D;

                @Config.Comment({
                                "Allow sim_wizard to add InsaneTweaks summon spells to its pool",
                                "(insanetweaks:summon_fer_cow, insanetweaks:summon_primitive_yelloweye).",
                                "Each target spell must also have npcs:true in its JSON; missing spells are skipped silently."
                })
                @Config.Name("Include Abomination Summons")
                public boolean includeAbominationSummons = true;

                @Config.Comment({
                                "v3.3: full spell pool as registry names. Edit freely - unknown ids are skipped with a",
                                "log warning, and an empty/broken list falls back to the built-in default pool.",
                                "Entries whose path starts with summon_ are additionally gated by Include Abomination",
                                "Summons above. Continuous spells (e.g. life_drain) are channeled automatically."
                })
                @Config.Name("Spell Pool")
                public String[] spellPool = {
                                "ebwizardry:magic_missile",
                                "ebwizardry:ice_shard",
                                "ebwizardry:force_orb",
                                "ebwizardry:spark_bomb",
                                "ebwizardry:heal",
                                "ebwizardry:life_drain",
                                "ebwizardry:banish",
                                "insanetweaks:summon_fer_cow",
                                "insanetweaks:summon_primitive_yelloweye"
                };

                @Config.Comment({
                                "Chance (%) per cast decision to use a 'special' spell when its situation applies:",
                                "banish when the target is within 4.5 blocks, life_drain at 4.5-9 blocks.",
                                "0 disables specials entirely."
                })
                @Config.Name("Special Spell Chance (%)")
                @Config.RangeInt(min = 0, max = 100)
                public int specialSpellChancePercent = 20;

                @Config.Comment("Below this HP percentage, the cast task prefers self-heal / summon-support over offensive projectiles.")
                @Config.Name("Retreat Health Percent")
                @Config.RangeInt(min = 0, max = 100)
                public int retreatHealthPercent = 30;

                @Config.Comment({
                                "Base flat cooldown (ticks) applied after every successful cast, before the spell's own",
                                "cooldown bonus. v3.2: 50 (~2.5 s) - the v2.3 value of 80 was tuned while a timing bug",
                                "tripled all cooldowns; with that bug fixed the wizard should cast at a lively cadence."
                })
                @Config.Name("Base Cast Cooldown (ticks)")
                @Config.RangeInt(min = 10, max = 600)
                public int baseCastCooldownTicks = 50;

                @Config.Comment({
                                "Maximum bonus cooldown (ticks) added from the spell's own getCooldown() value.",
                                "v3.2: 80, so even summons keep the wizard below ~6.5 s between casts."
                })
                @Config.Name("Max Spell Cooldown Bonus (ticks)")
                @Config.RangeInt(min = 0, max = 600)
                public int maxSpellCooldownBonusTicks = 80;

                @Config.Comment({
                                "Divisor applied to the spell's own getCooldown() before clamping with the bonus cap.",
                                "Higher = harsher nerf. v3.2 default: 2 (half the spell cooldown counts as bonus)."
                })
                @Config.Name("Spell Cooldown Divisor")
                @Config.RangeInt(min = 1, max = 10)
                public int spellCooldownDivisor = 2;

                @Config.Comment("Heal amount applied per passive self-heal tick (HP).")
                @Config.Name("Self Heal Amount")
                @Config.RangeDouble(min = 0.5, max = 20.0)
                public double selfHealAmount = 4.0D;

                @Config.Comment("Self-heal cooldown in ticks at normal HP. 20 ticks = 1 second.")
                @Config.Name("Self Heal Cooldown (ticks)")
                @Config.RangeInt(min = 20, max = 6000)
                public int selfHealCooldownNormal = 400;

                @Config.Comment("Self-heal cooldown in ticks when HP is below 10. Should be shorter than the normal cooldown.")
                @Config.Name("Self Heal Cooldown Low HP (ticks)")
                @Config.RangeInt(min = 10, max = 3000)
                public int selfHealCooldownLow = 150;

                @Config.Comment({
                                "Scale sim_wizard stats with the current SRP evolution phase.",
                                "When enabled, every phase above 0 adds phaseScalingPerPhase to health/armor/potency",
                                "(e.g. phase 2 with 0.10 multiplier per phase = +20% bonus on top of the base scaling)."
                })
                @Config.Name("Enable SRP Phase Scaling")
                public boolean enablePhaseScaling = true;

                @Config.Comment("Bonus multiplier per SRP phase. 0.10 = +10% per phase. Capped at maxPhase below.")
                @Config.Name("Phase Scaling Per Phase")
                @Config.RangeDouble(min = 0.0, max = 1.0)
                public double phaseScalingPerPhase = 0.10D;

                @Config.Comment("Highest SRP phase considered for the scaling bonus. Phases beyond this are clamped.")
                @Config.Name("Max Scaling Phase")
                @Config.RangeInt(min = 0, max = 10)
                public int phaseScalingMaxPhase = 4;

                @Config.Comment({
                                "SRP save-data dimension/data id used for evolution phase lookup.",
                                "Matches the value used by SrpWizardryAssimilationHelper for conversion (104).",
                                "Only touch this if your SRP install uses a different shared data id."
                })
                @Config.Name("SRP Save Data ID")
                @Config.RangeInt(min = 0, max = 1000)
                public int srpSaveDataId = 104;

                @Config.Comment({
                                "Ticks of charge-up vocalization played BEFORE every successful cast as a 'tell'.",
                                "Higher = easier to dodge. 0 disables the charge-up phase entirely."
                })
                @Config.Name("Cast Telegraph Ticks")
                @Config.RangeInt(min = 0, max = 80)
                public int castTelegraphTicks = 10;
        }

        // ========================================================================
        // PERK CONFIG HELPER (used by TombstoneTweaks)
        // ========================================================================
        public static class PerkConfig {
                @Config.Name("Enabled")
                @Config.Comment("If false, this perk will be shown as disabled (greyed out) in the Knowledge of Death GUI and cannot be levelled.")
                public boolean enabled;

                @Config.Name("Max Level")
                @Config.Comment("Maximum level cap for this perk. Cannot exceed the native maximum defined by Tombstone. Set to 0 to effectively disable it via level cap.")
                @Config.RangeInt(min = 0, max = 5)
                public int maxLevel;

                public PerkConfig(boolean enabled, int maxLevel) {
                        this.enabled = enabled;
                        this.maxLevel = maxLevel;
                }
        }

        // ========================================================================
        // CONFIG SYNC (auto-saves on GUI change)
        // ========================================================================
        @Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
        private static class EventHandler {
                @SubscribeEvent
                public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
                        if (event.getModID().equals(InsaneTweaksMod.MODID)) {
                                ConfigManager.sync(InsaneTweaksMod.MODID, Config.Type.INSTANCE);
                        }
                }
        }
}
