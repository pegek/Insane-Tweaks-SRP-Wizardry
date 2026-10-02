package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class EntitiesCategory {

    // Sub-category @Config.Name values double as .cfg section keys (Forge lowercases them),
    // so they stay snake_case; the pretty display names come from the LangKeys.
    @Config.Name("assimilated_wizard")
    @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard")
    @Config.Comment("The Assimilated Wizard parasite-mage (registry name sim_wizard): a full SRP parasite, aggressive vs non-parasites.")
    public final AssimilatedWizard assimilatedWizard = new AssimilatedWizard();

    @Config.Name("sentinel")
    @Config.LangKey("config.insanetweaks.category.entities.sentinel")
    @Config.Comment("The Sentinel ally battlemage: target priorities, pickup filter, regeneration.")
    public final Sentinel sentinel = new Sentinel();

    public static class Sentinel {
        @Config.Comment({ "Registry-name prefixes defining target priority, highest first.",
                "Entities matching an earlier prefix are attacked before later ones;",
                "undead rank after all listed prefixes; everything else last." })
        @Config.Name("Sentinel: Target Priority Prefixes")
        public String[] targetPriorityPrefixes = { "srparasites:", "srpextra:" };

        @Config.Comment({ "Registry-path keywords that count as 'valuable' for the pickup filter",
                "(used when a Sentinel's 'collect everything' toggle is OFF)." })
        @Config.Name("Sentinel: Valuable Keywords")
        public String[] valuableKeywords = { "ore", "ingot", "gem", "diamond", "emerald",
                "gold", "crystal", "dust", "wand", "scroll", "book", "pearl" };

        @Config.Comment("Out-of-combat self-heal amount per pulse (0 disables regeneration; HEALING-element Sentinels heal double).")
        @Config.Name("Sentinel: Regen Amount")
        @Config.RangeDouble(min = 0.0, max = 20.0)
        public double regenAmount = 3.75;
    }

    public static class AssimilatedWizard {

        @Config.Name("spawning")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.spawning")
        @Config.Comment("Master toggle, base attributes and SRP phase scaling. Attribute values apply to newly created entities only.")
        public final Spawning spawning = new Spawning();

        @Config.Name("combat")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.combat")
        @Config.Comment("Cast AI tunables: ranges, cooldowns, self-heal, telegraph.")
        public final Combat combat = new Combat();

        @Config.Name("spells")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.spells")
        @Config.Comment("Spell pool contents.")
        public final Spells spells = new Spells();

        @Config.Name("tiers")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.tiers")
        @Config.Comment("Power tiers: how often each one appears and how much stronger it is.")
        public final Tiers tiers = new Tiers();

        @Config.Name("tuning")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.tuning")
        @Config.Comment({
                "Fine-grained combat tuning that used to be hardcoded in EntityAISimWizardCombat.",
                "Every value here is read live on use - no restart, no world reload."
        })
        public final Tuning tuning = new Tuning();

        @Config.Name("battlemage")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.battlemage")
        @Config.Comment({
                "Overrides that apply to sim_battlemage ONLY. It inherits every value above; these",
                "are the deltas that make it read as a front-line caster instead of an artillery one."
        })
        public final Battlemage battlemage = new Battlemage();
    }

    /**
     * sim_battlemage behaves as a melee-first caster: while healthy it closes and swings its
     * spellblade, and it falls back on spellcasting once wounded or when the target is out of
     * reach. That split is what distinguishes it from sim_wizard, which is a pure artillery caster.
     */
    public static class Battlemage {

        @Config.Comment({
                "Above this health percentage the battlemage prefers melee over casting.",
                "Drop below it and it reverts to sim_wizard behaviour - spells, retreat, panic.",
                "Set to 0 to disable melee entirely and make it a plain caster."
        })
        @Config.Name("Melee Health Percent")
        @Config.RangeInt(min = 0, max = 100)
        public int meleeHealthPercent = 70;

        @Config.Comment({
                "Only engages in melee when the target is already within this many blocks.",
                "Beyond it the battlemage stays a caster, so it never abandons ranged pressure to",
                "sprint across a field. This is what keeps it casting SOME spells while healthy."
        })
        @Config.Name("Melee Engage Distance")
        @Config.RangeDouble(min = 1.0, max = 32.0)
        public double meleeEngageDistance = 6.0D;

        @Config.Comment({
                "Hysteresis: once engaged it keeps swinging out to this multiple of the engage",
                "distance, so a target stepping back one block does not flip it between modes.",
                "The AI task is polled every 3 ticks, so a hard cut-off would visibly stutter."
        })
        @Config.Name("Melee Disengage Multiplier")
        @Config.RangeDouble(min = 1.0, max = 4.0)
        public double meleeDisengageMultiplier = 1.6D;

        @Config.Comment({
                "Movement speed multiplier while closing for a melee attack.",
                "Unlike the rest of this category this one is read when the entity is CREATED",
                "(EntityAIAttackMelee stores it), so it applies to newly spawned battlemages only."
        })
        @Config.Name("Melee Move Speed")
        @Config.RangeDouble(min = 0.1, max = 4.0)
        public double meleeMoveSpeed = 1.15D;

        @Config.Comment("Base attack damage multiplier - it carries a spellblade, so it hits harder than a sim_human.")
        @Config.Name("Attack Damage Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double attackDamageMultiplier = 1.6D;

        @Config.Comment({
                "Every cast cooldown the battlemage pays is multiplied by this. Above 1.0 it casts",
                "less often than a sim_wizard, which is the point: its damage budget is in the blade.",
                "Read live - applies to the NEXT cooldown paid, not to one already running."
        })
        @Config.Name("Cast Cooldown Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double castCooldownMultiplier = 1.5D;
    }

    /**
     * All arrays here are indexed by tier ordinal: [0] = NOVICE, [1] = ADEPT, [2] = MASTER.
     * Missing entries fall back to 1.0, so a short array degrades instead of crashing.
     *
     * <p>NOVICE values must stay at exactly 1.0 unless you intend to change every existing
     * wizard in the world: a wizard saved before tiers existed loads as NOVICE.
     */
    public static class Tiers {

        @Config.Comment({
                "Relative chance of each tier, indexed NOVICE, ADEPT, MASTER.",
                "Read live - applies to newly created wizards."
        })
        @Config.Name("Tier Weights")
        public int[] tierWeights = { 60, 30, 10 };

        @Config.Comment({
                "SRP evolution phase divided by this gives EXTRA tier rolls; the best roll wins.",
                "With the default 2, phase 4 grants 2 extra rolls, so late-game wizards skew toward",
                "MASTER. This is how the evolution phase influences power - it decides WHICH tier",
                "appears, deliberately NOT how large each tier's bonus is, so the two never multiply",
                "into a runaway. Higher value = weaker phase influence. Read live."
        })
        @Config.Name("Tier Phase Roll Divisor")
        @Config.RangeInt(min = 1, max = 10)
        public int tierPhaseRollDivisor = 2;

        @Config.Comment({
                "Spell potency multiplier per tier, on top of Combat > Spell Potency Multiplier.",
                "This is the ONLY power term the evolution phase does not also multiply. Read live."
        })
        @Config.Name("Tier Potency Multipliers")
        public double[] tierPotencyMultipliers = { 1.0D, 1.2D, 1.5D };

        @Config.Comment("Max health multiplier per tier. Applied once at spawn.")
        @Config.Name("Tier Health Multipliers")
        @Config.RequiresWorldRestart
        public double[] tierHealthMultipliers = { 1.0D, 1.25D, 1.6D };

        @Config.Comment("Armor multiplier per tier. Applied once at spawn.")
        @Config.Name("Tier Armor Multipliers")
        @Config.RequiresWorldRestart
        public double[] tierArmorMultipliers = { 1.0D, 1.15D, 1.35D };

        @Config.Comment({
                "Hard ceiling on tier multiplier x SRP phase bonus for health. Health is the one",
                "stat where the two deliberately compound (it is a time-to-kill knob, not a burst",
                "knob), so it needs an upper bound."
        })
        @Config.Name("Max Combined Health Multiplier")
        @Config.RangeDouble(min = 1.0, max = 10.0)
        @Config.RequiresWorldRestart
        public double maxCombinedHealthMultiplier = 2.5D;

        @Config.Comment({
                "Optional whitelist of spell registry names a NOVICE wizard may use, filtered out of",
                "the main Spell Pool. EMPTY = no restriction (the default)."
        })
        @Config.Name("Novice Spell Filter")
        @Config.RequiresWorldRestart
        public String[] noviceSpellFilter = {};

        @Config.Comment("As above, for ADEPT. EMPTY = no restriction.")
        @Config.Name("Adept Spell Filter")
        @Config.RequiresWorldRestart
        public String[] adeptSpellFilter = {};

        @Config.Comment("As above, for MASTER. EMPTY = no restriction.")
        @Config.Name("Master Spell Filter")
        @Config.RequiresWorldRestart
        public String[] masterSpellFilter = {};

        @Config.Comment({
                "Give each tier its own glow colour, particle palette and focus item so the player",
                "can read a wizard's danger at a glance. Read live."
        })
        @Config.Name("Enable Tier Visuals")
        public boolean enableTierVisuals = true;
    }

    /**
     * Values read on every use inside the running combat task. None of them is cached in the
     * task's constructor, which is what makes the "read live" promise real - contrast
     * Combat.decisionRange / rangeMultiplier, which ARE cached there and therefore correctly
     * carry @Config.RequiresWorldRestart.
     */
    public static class Tuning {

        @Config.Comment("Maximum distance (blocks) at which a DISPLACE spell (banish) is chosen as the close-quarters answer.")
        @Config.Name("Displace Max Distance")
        @Config.RangeDouble(min = 1.0, max = 16.0)
        public double banishMaxDistance = 4.5D;

        @Config.Comment("Upper distance bound (blocks) for choosing a DRAIN spell (life_drain). Keep inside the ray's own range.")
        @Config.Name("Drain Max Distance")
        @Config.RangeDouble(min = 1.0, max = 32.0)
        public double lifeDrainMaxDistance = 9.0D;

        @Config.Comment("How long a continuous spell is channelled, in ticks. 20 ticks = 1 second.")
        @Config.Name("Channel Duration (ticks)")
        @Config.RangeInt(min = 5, max = 200)
        public int channelDurationTicks = 40;

        @Config.Comment("Closer than this (blocks), the wizard backs away instead of holding position.")
        @Config.Name("Retreat Distance")
        @Config.RangeDouble(min = 0.0, max = 32.0)
        public double retreatDistance = 7.0D;

        @Config.Comment({
                "Further than this (blocks), the wizard closes in. Between the two distances it",
                "holds still and lets the cast cooldown run. Clamped to be above Retreat Distance."
        })
        @Config.Name("Approach Distance")
        @Config.RangeDouble(min = 4.0, max = 64.0)
        public double approachDistance = 18.0D;

        @Config.Comment("Ticks between navigation updates while manoeuvring. Lower = twitchier and more path recalculation.")
        @Config.Name("Repath Interval (ticks)")
        @Config.RangeInt(min = 1, max = 60)
        public int repathIntervalTicks = 10;

        @Config.Comment("Movement speed multiplier when closing in on a target.")
        @Config.Name("Approach Speed")
        @Config.RangeDouble(min = 0.1, max = 3.0)
        public double moveSpeed = 1.0D;

        @Config.Comment("Movement speed multiplier when backing away from a target.")
        @Config.Name("Retreat Speed")
        @Config.RangeDouble(min = 0.1, max = 3.0)
        public double retreatSpeed = 1.25D;

        @Config.Comment("Radius (blocks) of the forward cone searched for clustered enemies before choosing an AoE spell.")
        @Config.Name("AoE Cone Radius")
        @Config.RangeDouble(min = 2.0, max = 32.0)
        public double clusterConeRadius = 8.0D;

        @Config.Comment("Half-angle of that cone, in DEGREES. 60 means a 120-degree forward arc.")
        @Config.Name("AoE Cone Half-Angle (degrees)")
        @Config.RangeInt(min = 5, max = 180)
        public int clusterConeAngleDegrees = 60;

        @Config.Comment("How many valid targets must stand in the cone before an AoE spell is preferred.")
        @Config.Name("AoE Minimum Targets")
        @Config.RangeInt(min = 2, max = 10)
        public int clusterMinTargets = 2;

        @Config.Comment({
                "Chance (%) that a met situational condition actually overrides the distance bands.",
                "These exist to stop a single always-true condition from flattening the whole fight",
                "onto one spell: the SLOW branch used to fire on every moving target, so ice_shard was",
                "chosen ~90% of the time and force_orb / spark_bomb / life_drain never came up at all.",
                "100 restores the old deterministic behaviour, 0 disables the override entirely."
        })
        @Config.Name("Situational Override Chance (%)")
        @Config.RangeInt(min = 0, max = 100)
        public int situationalOverrideChancePercent = 55;

        @Config.Comment({
                "Squared lateral speed (blocks/tick) above which a target counts as 'fast moving' and is",
                "met with a SLOW spell.",
                "CALIBRATION: a vanilla player WALKS at ~0.216 b/t, so the old 0.04 (= 0.2 b/t) treated",
                "ordinary walking as sprinting. The default 0.09 (= 0.3 b/t) sits above vanilla sprint",
                "(~0.28 b/t), so this check now catches genuinely fast mobs; players are already covered",
                "by the explicit isSprinting() and Speed-potion checks."
        })
        @Config.Name("Fast Target Speed Threshold (squared)")
        @Config.RangeDouble(min = 0.001, max = 1.0)
        public double fastTargetSpeedSquared = 0.09D;

        @Config.Comment("A target above this health percentage and within the distance below is met with a KNOCKBACK opener.")
        @Config.Name("Knockback Opener Health (%)")
        @Config.RangeInt(min = 0, max = 100)
        public int knockbackTargetHealthPercent = 85;

        @Config.Comment("Maximum distance (blocks) for that knockback opener.")
        @Config.Name("Knockback Opener Max Distance")
        @Config.RangeDouble(min = 1.0, max = 32.0)
        public double knockbackMaxDistance = 6.0D;

        @Config.Comment("Upper bound (blocks) of the close-range spell band.")
        @Config.Name("Short Band Distance")
        @Config.RangeDouble(min = 1.0, max = 32.0)
        public double shortBandDistance = 6.0D;

        @Config.Comment("Upper bound (blocks) of the mid-range spell band. Beyond it the long-range band applies.")
        @Config.Name("Medium Band Distance")
        @Config.RangeDouble(min = 2.0, max = 64.0)
        public double mediumBandDistance = 14.0D;

        @Config.Comment("Length of the cast animation / glow flare window, in ticks.")
        @Config.Name("Cast Animation (ticks)")
        @Config.RangeInt(min = 1, max = 80)
        public int castAnimationTicks = 14;

        @Config.Comment("Slowness applied to the wizard after a cast, in ticks. 0 disables the rooting entirely.")
        @Config.Name("Post-Cast Slowness (ticks)")
        @Config.RangeInt(min = 0, max = 200)
        public int postCastSlownessTicks = 20;

        @Config.Comment("Amplifier of that slowness. 0 = Slowness I, 1 = Slowness II.")
        @Config.Name("Post-Cast Slowness Level")
        @Config.RangeInt(min = 0, max = 4)
        public int postCastSlownessAmplifier = 1;

        @Config.Comment("Cooldown after a cast that failed outright (spell refused, target gone during telegraph).")
        @Config.Name("Failed Cast Cooldown (ticks)")
        @Config.RangeInt(min = 1, max = 200)
        public int failedCastCooldownTicks = 10;

        @Config.Comment("Cooldown after a cast that another mod vetoed through SpellCastEvent and the arbiter upheld.")
        @Config.Name("Vetoed Cast Cooldown (ticks)")
        @Config.RangeInt(min = 1, max = 400)
        public int eventBlockCooldownTicks = 20;

        @Config.Comment({
                "Floor applied when the combat task stops, and when a cast is postponed for lack of",
                "line of sight. Kept short on purpose so cover does not act as a cast-rate nerf."
        })
        @Config.Name("Minimum Retry Cooldown (ticks)")
        @Config.RangeInt(min = 1, max = 100)
        public int minRetryCooldownTicks = 5;

        // ---------------- Panic reaction ----------------

        @Config.Comment({
                "React when cornered or badly hurt: abort whatever is being cast and answer with an",
                "ESCAPE spell, or a DISPLACE spell (banish) when no escape is in the pool.",
                "The wizard stays a pure caster - it never swings back."
        })
        @Config.Name("Enable Panic Reaction")
        public boolean enablePanicReaction = true;

        @Config.Comment("A target this close (blocks) counts as cornering the wizard.")
        @Config.Name("Panic Distance")
        @Config.RangeDouble(min = 0.0, max = 8.0)
        public double panicDistance = 2.5D;

        @Config.Comment("Fraction of MAX health that must be lost in one hit to count as a heavy blow. 0.20 = 20%.")
        @Config.Name("Panic Damage Fraction")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double panicDamageFraction = 0.20D;

        @Config.Comment("How long (ticks) a heavy blow keeps the panic reaction armed.")
        @Config.Name("Panic Damage Window (ticks)")
        @Config.RangeInt(min = 5, max = 100)
        public int panicWindowTicks = 20;

        @Config.Comment({
                "Cooldown (ticks) between panic reactions. Deliberately long - a short value lets the",
                "wizard chain escapes and become untouchable."
        })
        @Config.Name("Panic Cooldown (ticks)")
        @Config.RangeInt(min = 20, max = 600)
        public int panicCooldownTicks = 120;

        // ---------------- Ally support ----------------

        @Config.Comment({
                "Let the wizard heal wounded parasite allies with an ALLY_HEAL spell (group_heal).",
                "Requires such a spell in the Spell Pool - plain 'heal' will NOT work, because EBW's",
                "SpellBuff applies to the caster and ignores everyone else."
        })
        @Config.Name("Enable Ally Support")
        public boolean enableAllySupport = true;

        @Config.Comment("Radius (blocks) searched for wounded parasite allies.")
        @Config.Name("Ally Support Radius")
        @Config.RangeDouble(min = 2.0, max = 32.0)
        public double supportRadius = 8.0D;

        @Config.Comment("How many wounded allies must be in range before the wizard spends a cast on them.")
        @Config.Name("Ally Support Minimum Wounded")
        @Config.RangeInt(min = 1, max = 16)
        public int supportMinWoundedAllies = 2;

        @Config.Comment("An ally at or below this health percentage counts as wounded.")
        @Config.Name("Ally Support Health Threshold (%)")
        @Config.RangeInt(min = 1, max = 99)
        public int supportAllyHealthPercent = 60;

        @Config.Comment("Cooldown (ticks) between ally-support casts, independent of the combat cast cooldown.")
        @Config.Name("Ally Support Cooldown (ticks)")
        @Config.RangeInt(min = 20, max = 1200)
        public int supportCooldownTicks = 200;
    }

    public static class Spawning {
        @Config.Comment({
                "Master toggle for the Assimilated Wizard entity (registry name sim_wizard).",
                "When false, the SrpWizardryAssimilationHelper bridge falls back to srparasites:sim_human",
                "for both ebwizardry:wizard and ebwizardry:evil_wizard conversions."
        })
        @Config.Name("Enable Sim Wizard")
        @Config.RequiresMcRestart
        public boolean enabled = true;

        @Config.Comment("Multiplier applied on top of EntityInfHuman base health (SRPAttributes.INFHUMAN_HEALTH).")
        @Config.Name("Health Multiplier")
        @Config.RangeDouble(min = 0.5, max = 5.0)
        @Config.RequiresWorldRestart
        public double healthMultiplier = 1.15D;

        @Config.Comment("Flat extra HP added on top of the multiplied base health.")
        @Config.Name("Extra Health (flat)")
        @Config.RangeDouble(min = 0.0, max = 50.0)
        @Config.RequiresWorldRestart
        public double extraHealth = 2.5D;

        @Config.Comment({
                "Multiplier applied on top of EntityInfHuman base armor.",
                "Values < 1.0 reduce armor below base."
        })
        @Config.Name("Armor Multiplier")
        @Config.RangeDouble(min = 0.0, max = 5.0)
        @Config.RequiresWorldRestart
        public double armorMultiplier = 0.70D;

        @Config.Comment("Multiplier applied on top of EntityInfHuman base movement speed.")
        @Config.Name("Movement Speed Multiplier")
        @Config.RangeDouble(min = 0.5, max = 3.0)
        @Config.RequiresWorldRestart
        public double speedMultiplier = 1.0D;

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
                "Which entities SRP assimilation turns into what, one entry per line:",
                "  '<source id>=<target id>[:<TIER>]'",
                "The optional TIER (NOVICE, ADEPT, MASTER) is a MINIMUM - the wizard still rolls its",
                "own tier and only gets raised to this floor, so an evil or class wizard is never",
                "weaker than its origin implies.",
                "",
                "Setting 'Enable Sim Wizard' to false makes every insanetweaks:sim_wizard target fall",
                "back to srparasites:sim_human, tier floors included.",
                "Unknown ids are logged and skipped. Read live."
        })
        @Config.Name("Assimilation Map")
        public String[] assimilationMap = {
                "ebwizardry:wizard=insanetweaks:sim_wizard",
                "ebwizardry:evil_wizard=insanetweaks:sim_wizard:ADEPT",
                "ancientspellcraft:class_wizard=insanetweaks:sim_battlemage",
                "ancientspellcraft:evil_class_wizard=insanetweaks:sim_battlemage:MASTER"
        };

        @Config.Comment({
                "Experience dropped when a sim_wizard is killed. Vanilla evoker drops 10.",
                "The mod's other entities drop 0 because they are all summons - this is the only",
                "genuinely hostile one. 0 disables XP. Read live."
        })
        @Config.Name("Experience Value")
        @Config.RangeInt(min = 0, max = 100)
        public int experienceValue = 12;

        @Config.Comment({
                "Drop loot from the mod's own per-tier loot tables on death.",
                "Turn off to fall back to whatever EntityInfHuman drops. Read live."
        })
        @Config.Name("Enable Loot Table")
        public boolean enableLootTable = true;

        @Config.Comment({
                "Always leave SRP remains (gore block + EntityRemain) on death.",
                "",
                "SRP itself makes this a COIN FLIP, not a certainty: EntityParasiteBase starts with",
                "madeRng = -1 and rolls nextInt(2) the first time the parasite is damaged. On a 1,",
                "onDeathUpdate takes the plain branch and no remains are produced at all. Every",
                "vanilla SRP parasite behaves this way - so does sim_wizard.",
                "",
                "false (default) leaves that native 50/50 untouched, so this mod's casters die on",
                "exactly the same terms as any other parasite. Set true to force the losing roll",
                "back to 0 and make remains guaranteed.",
                "Read live - affects entities damaged after the change."
        })
        @Config.Name("Always Leave Remains")
        public boolean guaranteedRemains = false;
    }

    public static class Combat {
        @Config.Comment("Minimum follow / aggro range (blocks). Forced when SRP infectedFollow is lower.")
        @Config.Name("Min Follow Range")
        @Config.RangeInt(min = 8, max = 64)
        @Config.RequiresWorldRestart
        public int minFollowRange = 32;

        @Config.Comment("Maximum spell-cast decision range (blocks). Beyond this distance the wizard reverts to navigation/melee.")
        @Config.Name("Spell Decision Range")
        @Config.RangeInt(min = 4, max = 64)
        @Config.RequiresWorldRestart
        public int decisionRange = 24;

        @Config.Comment("Spell potency multiplier passed via SpellModifiers.POTENCY.")
        @Config.Name("Spell Potency Multiplier")
        @Config.RangeDouble(min = 1.0, max = 3.0)
        public double potencyMultiplier = 1.175D;

        @Config.Comment("Spell range multiplier passed via SpellModifiers \"range\" key.")
        @Config.Name("Spell Range Multiplier")
        @Config.RangeDouble(min = 1.0, max = 3.0)
        @Config.RequiresWorldRestart
        public double rangeMultiplier = 1.175D;

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

        @Config.Comment("Base flat cooldown (ticks) applied after every successful cast, before the spell's own cooldown bonus.")
        @Config.Name("Base Cast Cooldown (ticks)")
        @Config.RangeInt(min = 10, max = 600)
        public int baseCastCooldownTicks = 50;

        @Config.Comment("Maximum bonus cooldown (ticks) added from the spell's own getCooldown() value.")
        @Config.Name("Max Spell Cooldown Bonus (ticks)")
        @Config.RangeInt(min = 0, max = 600)
        public int maxSpellCooldownBonusTicks = 80;

        @Config.Comment({
                "Divisor applied to the spell's own getCooldown() before clamping with the bonus cap.",
                "Higher = harsher nerf."
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
                "Ticks of charge-up vocalization played BEFORE every successful cast as a 'tell'.",
                "Higher = easier to dodge. 0 disables the charge-up phase entirely."
        })
        @Config.Name("Cast Telegraph Ticks")
        @Config.RangeInt(min = 0, max = 80)
        public int castTelegraphTicks = 10;

        @Config.Comment({
                "Require an unobstructed line of sight before casting a target-dependent spell.",
                "When the target is behind cover the wizard closes in instead of firing through walls.",
                "Self-targeted spells (heal and other buffs) and summons are exempt.",
                "Set to false to restore the old behaviour of casting through terrain.",
                "Read live - no restart needed."
        })
        @Config.Name("Require Line Of Sight")
        public boolean requireLineOfSight = true;

        @Config.Comment({
                "Ticks of remembered line of sight. A cast is still allowed this long after the",
                "target was last visible, so a fence post or a sapling does not waste the whole",
                "charge-up. 0 = require sight on the exact firing tick. Read live."
        })
        @Config.Name("Line Of Sight Grace (ticks)")
        @Config.RangeInt(min = 0, max = 60)
        public int lineOfSightGraceTicks = 10;
    }

    public static class Spells {
        @Config.Comment({
                "Allow the Assimilated Wizard to add InsaneTweaks summon spells to its pool",
                "(insanetweaks:summon_fer_cow, insanetweaks:summon_primitive_yelloweye).",
                "Each target spell must also have npcs:true in its JSON; missing spells are skipped silently."
        })
        @Config.Name("Include Abomination Summons")
        @Config.RequiresWorldRestart
        public boolean includeAbominationSummons = true;

        @Config.Comment({
                "Full spell pool as registry names. Edit freely - unknown ids are skipped with a",
                "log warning, and an empty/broken list falls back to the built-in default pool.",
                "Entries whose path starts with summon_ are additionally gated by Include Abomination",
                "Summons above. Continuous spells (e.g. life_drain) are channeled automatically."
        })
        @Config.Name("Spell Pool")
        @Config.RequiresWorldRestart
        public String[] spellPool = {
                "ebwizardry:magic_missile",
                "ebwizardry:ice_shard",
                "ebwizardry:force_orb",
                "ebwizardry:spark_bomb",
                "ebwizardry:heal",
                "ebwizardry:life_drain",
                "ebwizardry:banish",
                "ebwizardry:group_heal",
                "insanetweaks:summon_fer_cow",
                "insanetweaks:summon_primitive_yelloweye"
        };

        @Config.Comment({
                "Tactical role overrides, one entry per line: '<registry_name>=ROLE[,ROLE]'.",
                "Roles drive WHICH spell the AI picks in a given situation, so the tactics keep",
                "working after you edit the Spell Pool above. Roles are normally DERIVED from the",
                "spell's own metadata (class, type, tier, properties) - list a spell here only to",
                "correct or extend that guess.",
                "",
                "Valid roles: PROJECTILE_SHORT, PROJECTILE_LONG, AOE, KNOCKBACK, SLOW, DRAIN,",
                "DISPLACE, ESCAPE, SELF_HEAL, SELF_BUFF, ALLY_HEAL, SUMMON.",
                "",
                "The defaults below only spell out what cannot be derived: SLOW (ice_shard's",
                "slowness lives in its projectile entity, not in any property) and ESCAPE (blink is",
                "a bare Spell with a single range property). The rest are listed for documentation",
                "and match what derivation produces anyway.",
                "Unknown ids or role names are logged and skipped. Read live - no restart needed."
        })
        @Config.Name("Spell Roles")
        public String[] spellRoles = {
                "ebwizardry:ice_shard=SLOW,PROJECTILE_LONG",
                "ebwizardry:magic_missile=PROJECTILE_LONG",
                "ebwizardry:spark_bomb=AOE,PROJECTILE_SHORT",
                "ebwizardry:force_orb=KNOCKBACK,PROJECTILE_SHORT",
                "ebwizardry:banish=DISPLACE",
                "ebwizardry:life_drain=DRAIN",
                "ebwizardry:heal=SELF_HEAL",
                "ebwizardry:group_heal=ALLY_HEAL",
                "ebwizardry:blink=ESCAPE"
        };
    }
}
