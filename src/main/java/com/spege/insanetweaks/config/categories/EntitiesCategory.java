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
                "insanetweaks:summon_fer_cow",
                "insanetweaks:summon_primitive_yelloweye"
        };
    }
}
