package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class TweaksCategory {

    @Config.Comment({
            "Prevents the Enigmatic Legacy 'Cursed Ring' from forcing summoned creatures (e.g. Fer Cow Minion) to attack their own caster.",
            "When enabled, all Electroblob's Wizardry summoned creatures correctly report as being 'on the same team' as their owner,",
            "which makes the Cursed Ring's anger loop skip them entirely.",
            "Safe to leave enabled even if Enigmatic Legacy is not installed — the fix costs nothing when the ring is absent.",
            "This is a sub-toggle of 'Enable Enigmatic Legacy Interactions' (interactions category): if that master switch is OFF, this fix is off regardless." })
    @Config.Name("Enable Cursed Ring Minion Fix")
    public boolean enableCursedRingFix = true;

    @Config.Comment({
            "Additional effects removed by the CLEANSE effect, on top of (a) all effects where",
            "isBeneficial() == false and (b) the built-in parasite-effect list shipped in the mod",
            "(all harmful SRParasites/SRPExtra effects are already covered by the built-in list).",
            "Add effect IDs here only if some other mod's negative effect is not removed automatically.",
            "Example: minecraft:glowing" })
    @Config.Name("Cleanse Effect List")
    public String[] cleanseAdditionalEffects = {};

    @Config.Comment({"Master toggle for the Zhonya's Hourglass artefact (the player-stasis one).",
            "When false (default), the artefact is inert: right-click does nothing and the",
            "tooltip shows a 'disabled' line. (It is also flagged disabled to EB Wizardry's",
            "artefact API as future-proofing.)",
            "The item stays registered, so existing copies in worlds are unaffected.",
            "The Restoration Hourglass is NOT affected by this switch."})
    @Config.Name("Enable Zhonya's Hourglass")
    @Config.RequiresMcRestart
    public boolean enableZhonya = false;

    @Config.Comment({"Zhonya's Hourglass: cooldown after activation, in ticks.",
            "Default 216000 = 3 hours."})
    @Config.Name("Zhonya Cooldown Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaCooldownTicks = 216000;

    @Config.Comment({"Zhonya's Hourglass: Gilded Stasis duration in ticks (default 60 = 3 s)."})
    @Config.Name("Zhonya Stasis Duration Ticks")
    @Config.RangeInt(min = 1)
    public int zhonyaStasisTicks = 60;

    @Config.Comment({"Zhonya's Hourglass: aggro-loss window in ticks (default 100 = 5 s).",
            "During this window all mobs targeting the user are de-aggroed every tick."})
    @Config.Name("Zhonya Aggro Loss Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaAggroLossTicks = 100;

    @Config.Comment({"Zhonya's Hourglass: capacity of the artefact's own built-in EB mana pool",
            "(default 3000). A full charge is required to activate and is fully consumed."})
    @Config.Name("Zhonya EB-Mana Capacity")
    @Config.RangeInt(min = 1)
    public int zhonyaEbManaCapacity = 3000;

    @Config.Comment({"Restoration Hourglass: log the origin-snapshot pipeline (what a parasite was",
            "before SRP infected it, and what it restores to). Off by default because these lines sit",
            "on a per-entity-join path - on a parasite-heavy pack that is thousands of lines a minute.",
            "Real problems are still reported as warnings regardless of this flag. Read live."})
    @Config.Name("Restoration Debug Logging")
    public boolean restorationDebugLogging = false;

    @Config.Comment({"Restoration Hourglass: enable its crafting recipe. Without it the artefact has no",
            "acquisition path at all beyond /give - there is no loot table or quest reward for it."})
    @Config.Name("Restoration Hourglass Recipe")
    @Config.RequiresMcRestart
    public boolean restorationHourglassRecipe = true;

    @Config.Comment({
            "Makes Electroblob's Imbuement Altar breakable with a pickaxe. EB ships it as",
            "setBlockUnbreakable() (hardness -1), i.e. bedrock in survival, because it was meant to be",
            "a ruin you restore in place rather than a block you own. Ancient Spellcraft later added a",
            "crafting recipe for it, which leaves the altar craftable but impossible to reclaim - a",
            "misplaced one is gone for good.",
            "Blast resistance is deliberately NOT touched: the altar stays creeper-proof either way.",
            "Breaking it never destroys the item sitting on it - EB's own breakBlock drops that,",
            "independently of everything below." })
    @Config.Name("Imbuement Altar Breakable")
    @Config.RequiresMcRestart
    public boolean imbuementAltarBreakable = true;

    @Config.Comment({"Imbuement Altar: block hardness once breakable. 3.0 matches stone and Ancient",
            "Spellcraft's own 'ruined imbuement altar'.",
            "Ignored when 'Imbuement Altar Breakable' is off."})
    @Config.Name("Imbuement Altar Hardness")
    @Config.RangeDouble(min = 0.1D, max = 100.0D)
    @Config.RequiresMcRestart
    public double imbuementAltarHardness = 3.0D;

    @Config.Comment({"Imbuement Altar: pickaxe tier needed to mine it at tool speed (0=wood, 1=stone,",
            "2=iron, 3=diamond). 🚨 This is NOT cosmetic: EB registers no harvest tool for the altar,",
            "and a ROCK-material block with no tool class breaks at bare-hand speed AND drops nothing,",
            "because canHarvestBlock is false. Setting it is what makes 'mine it with a pickaxe' true.",
            "Ignored when 'Imbuement Altar Breakable' is off."})
    @Config.Name("Imbuement Altar Harvest Level")
    @Config.RangeInt(min = 0, max = 4)
    @Config.RequiresMcRestart
    public int imbuementAltarHarvestLevel = 2;

    @Config.Comment({"Imbuement Altar: EB Wizardry artefacts that let a player RECLAIM the altar as an",
            "item instead of destroying it. Without one of these equipped the altar breaks and drops",
            "nothing - so world-generated altars in library ruins cannot simply be farmed, and",
            "reclaiming one stays a deliberate, geared act.",
            "Read live; entries must be EB Wizardry artefacts (rings/amulets/charms). Anything else is",
            "ignored with one warn line. An empty list means the altar can never be reclaimed.",
            "Default is the Charm of Silk Touch, which is already what makes EB's own Mine spell",
            "silk-harvest - so the semantics carry over unchanged."})
    @Config.Name("Imbuement Altar Salvage Artefacts")
    public String[] imbuementAltarSalvageArtefacts = { "ebwizardry:charm_silk_touch" };

    @Config.Comment({
            "Master switch for Imbuement Altar RITUALS: a 3x3 field of ebwizardry:imbuement_altar",
            "blocks that behaves like a crafting table. Put one item on each altar; when the",
            "arrangement matches a ritual the structure animates and the result appears on the centre",
            "altar.",
            "Read live for the tick loop, but the recipe list is built once at startup - turning this",
            "ON after launch gives you a structure with nothing registered to match, so restart.",
            "Does not interfere with EB's own imbuement: that needs four receptacles around a single",
            "altar, and in a 3x3 field every neighbour is another altar, so no altar is ever ACTIVE." })
    @Config.Name("Enable Altar Rituals")
    @Config.RequiresMcRestart
    public boolean enableAltarRituals = true;

    @Config.Comment({"Altar rituals: how long a ritual runs before it produces its result, in ticks.",
            "140 (7 s) matches EB's own imbuement animation.",
            "Applied when the recipes are built, so it takes a restart."})
    @Config.Name("Ritual Duration Ticks")
    @Config.RangeInt(min = 20, max = 6000)
    @Config.RequiresMcRestart
    public int ritualDurationTicks = 140;

    // The Bauble Fruit acquisition loop (fragment drops, sapling, corrupted fruit) moved to the
    // dedicated 'baubleFruits' category in 1.4.18 — see BaubleFruitsCategory.
}
