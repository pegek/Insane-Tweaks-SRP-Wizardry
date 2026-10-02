package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class EbwCategory {

    @Config.Comment({
            "Whether to hook Electroblob's Wizardry spell cost into the player's mana pool.",
            "This does NOT switch off the EBW mixins - those apply whenever EBW is installed,",
            "because a config value cannot gate mixin application. The flag controls only whether",
            "ManaCore registers its own event handlers, which is why it needs a restart."})
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("Global multiplier on spell cost, applied on top of EBW's own modifiers. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double costMultiplier = 1.0D;

    @Config.Comment("Whether to respect COST attributes from the wizardryutils mod, when present. Works live, no restart.")
    public boolean useWizardryUtilsAttributes = true;

    @Config.Comment({
            "Whether a higher-tier wand refunds part of a spell's cost. Works live, no restart.",
            "OFF by default: in play the refund turned out far too strong, and the mechanic is",
            "under review - the current thinking is to replace it with a fatigue system, where a",
            "better wand softens the penalty for casting rapidly instead of handing mana back.",
            "The `refund*` settings below do nothing while this is off."})
    public boolean refundEnabled = false;

    @Config.Comment({
            "Wand capacity below which the `storage` upgrade grants no refund at all. Works live, no restart.",
            "Setting this above any capacity a wand can actually reach disables the refund entirely,",
            "silently - nothing in game says why it stopped working. Real EBW wand capacities are in",
            "the hundreds, so the upper end of this range is far past anything useful."})
    @Config.RangeInt(min = 0, max = 100000)
    public int refundBaselineCapacity = 100;

    @Config.Comment("Every this many points of capacity surplus, `refundFractionPerStep` is credited. Works live, no restart.")
    @Config.RangeInt(min = 1, max = 100000)
    public int refundCapacityStep = 100;

    @Config.Comment("Fraction of cost refunded per step of wand capacity surplus. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double refundFractionPerStep = 0.05D;

    @Config.Comment("How much mana per second one level of the `condenser` upgrade grants. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double condenserRegenPerLevel = 0.5D;

    @Config.Comment("How much mana one level of the `siphon` upgrade grants per kill. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double siphonManaPerLevel = 5.0D;

    @Config.Comment({
            "How much mana per second the `ring_condensing` artefact grants. Works live, no restart.",
            "In EBW the ring recharged every wand on the hotbar, which stopped mattering once spells",
            "were paid for from the player's pool - this is that effect, redirected."})
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double ringCondensingRegenPerSecond = 0.5D;

    @Config.Comment({
            "How much mana per second the `amulet_arcane_defence` artefact grants. Works live, no restart.",
            "Same story as `ringCondensingRegenPerSecond`: in EBW it recharged worn wizard armour."})
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double amuletArcaneDefenceRegenPerSecond = 0.5D;

    @Config.Comment({
            "Multiplier the `ring_siphoning` artefact applies to mana gained from kills. Works live.",
            "The default is EBW's own figure - its handler multiplies siphoned mana by exactly 1.3.",
            "Applies to the `siphon` payout only, never to passive regeneration; EBW keeps those apart."})
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double ringSiphoningMultiplier = 1.3D;

    @Config.Comment({
            "Whether Necromancer's Delight's Leechlink Amulet returns stolen mana to the owner's",
            "mana pool instead of to the item in their hand. Read live, no restart - but the mixin",
            "itself applies whenever Necromancer's Delight is installed; this flag only picks where",
            "the mana goes. The amount is the mod's own: 50% of what the Mana Leech stole."})
    public boolean leechlinkToPool = true;

    @Config.Comment({
            "Whether reserve sources may pay the part of a spell's cost the mana pool cannot.",
            "In EBW and Ancient Spellcraft these artefacts fired when the WAND ran dry; with the pool",
            "paying for spells that wand mana is frozen, so they now fire when the POOL cannot pay.",
            "Off restores nothing upstream - the original wand-based triggers stay disabled either",
            "way, because they read mana that no longer moves. Read live, no restart."})
    public boolean fuelEnabled = true;

    @Config.Comment({
            "Equipped artefacts that act as a mana reserve, by registry name. Each must store mana",
            "(EBW's IManaStoringItem). They pay only the missing difference, drawing from the first",
            "one listed before the next. Read live, no restart."})
    public String[] fuelArtefacts = new String[] {
            "ancientspellcraft:ring_mana_lesser",
            "ancientspellcraft:ring_mana_greater",
            "ancientspellcraft:charm_majestic_mana"
    };

    @Config.Comment({
            "How much mana stored in a reserve artefact pays for 1 mana of a spell. Read live.",
            "The artefacts keep their upstream capacities (500 / 1000 / 2500) - those are stored as",
            "item damage, so shrinking them would leave charged stacks with negative mana. At 5, the",
            "Majestic Mana Charm's 2500 is worth 500 pool mana."})
    @Config.RangeDouble(min = 0.01D, max = 1000.0D)
    public double fuelArtefactManaPerPoolMana = 5.0D;

    @Config.Comment({
            "How much mana one hunger point pays while the Demonic Seal (charm_hunger_casting) is",
            "worn. Read live. 5 is EBW's own rate (cost / 5); a full hunger bar then covers 100 mana.",
            "Hunger is drawn after reserve artefacts, rounded up, and never for continuous spells."})
    @Config.RangeDouble(min = 0.01D, max = 1000.0D)
    public double hungerManaPerPoint = 5.0D;

    @Config.Comment({
            "How much maximum mana each level of the storage upgrade adds while the wand is held.",
            "Read live. Holding two wands counts the higher level, not the sum. 0 disables."})
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double storageBonusPerLevel = 15.0D;

    @Config.Comment({
            "Fraction of a newly gained storage bonus that is added to current mana at once, so",
            "picking the wand up is immediately useful rather than only raising an empty ceiling.",
            "Read live. 0 disables the refill; the cap increase still applies."})
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double storageFillFraction = 0.3D;

    @Config.Comment({
            "Minimum seconds between two storage refills for one player. Read live.",
            "Without it, putting the wand away and taking it out again would be free mana: the",
            "overflow above the lowered cap is confiscated, but the refill would come back each time."})
    @Config.RangeInt(min = 0, max = 86400)
    public int storageFillCooldownSeconds = 60;
}
