package com.spege.srpwizmixins.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Scape and Run: Parasites (srparasites) native-patch compatibility module.
 *
 * <p>Ports the most valuable SRPMixins (2.9.5, target SRP 1.9.21) tweaks onto our
 * SRParasites 1.10.7 (community) build as InsaneTweaks mixins. Every fix is gated
 * behind its own toggle and defaults to OFF so the module is inert until opted in.
 *
 * <p>All the SRP-targeting mixins live in {@code mixins.srpwizmixins.json}, which
 * {@code SrpWizMixinsLateBooter} only queues when {@code srparasites} is present.
 *
 * <p>IMPORTANT — the flags below are NOT mixin gates. {@code mixins.srpwizmixins.json} declares no
 * {@code plugin}, so all eight mixins are applied unconditionally once SRP is present; every flag
 * here is an early-return at the top of the injected handler. Two consequences:
 * <ul>
 *   <li>Toggling a flag takes effect immediately — none of them need a restart.</li>
 *   <li>Anything that can go wrong at mixin <em>application</em> time (a {@code VerifyError} from a
 *       bad merged {@code <clinit>}, a missing injection point) happens whether the flag is on or
 *       off. A crash is never excused by "but that fix was disabled".</li>
 * </ul>
 * Turning a fix off still makes the patched method behave exactly like unmodified SRP. Making these
 * real application gates would need an {@code IMixinConfigPlugin}; see the note in
 * {@code com.spege.srpwizmixins.util.SrpLocks} for why the current arrangement is the safer one.
 */
public class SrpCompatCategory {

    @Config.Comment({
            "Extra logging for this mod, for when something is not behaving and you want to know why.",
            "Logs what actually removed a Beckon or Nexus, and how each dimension's evolution points",
            "were set up at world load.",
            "Very noisy - only turn it on while investigating something. No restart needed. Default OFF."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;

    @Config.Comment({
            "Stops parasites vanishing in front of you when the population hits its cap.",
            "When there are too many parasites in a dimension, SRP deletes some of them - and it does",
            "not check whether they were meant to stay, so a Beckon or Nexus you were fighting can",
            "disappear mid-fight.",
            "With this ON, Beckons and Nexuses are never deleted this way, and no parasite is deleted",
            "while it is close to a player (see the two radius options below). Parasites far from",
            "everyone are still removed, so the cap still does its job.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Protect Non-Despawnable From Cap Purge")
    public boolean protectNonDespawnableFromCapPurge = false;

    @Config.Comment({
            "How close (in blocks) an ordinary parasite has to be to a player to be spared from the",
            "over-population cull. Only used when the fix above is ON.",
            "Lower this if a big horde right next to you is hurting performance. 0 = no protection",
            "for ordinary parasites at all. No restart needed."
    })
    @Config.Name("Cap Purge Protect Radius")
    @Config.RangeInt(min = 0, max = 256)
    public int capPurgeProtectRadius = 48;

    @Config.Comment({
            "The same thing for Beckons and Nexuses, which normally deserve a wider safety zone so",
            "they are not wiped out from across the map. Beyond this distance even they can be culled,",
            "so they do not pile up forever in chunks nobody visits.",
            "0 = they get no special protection. No restart needed."
    })
    @Config.Name("Beckon/Nexus Cap Purge Radius")
    @Config.RangeInt(min = 0, max = 2048)
    public int beckonCapPurgeRadius = 200;

    @Config.Comment({
            "Makes SRP actually use the starting evolution points you configured per dimension.",
            "In SRP's own config you can write 'dimension;phase;points' to give a dimension a head",
            "start - but on 1.10.7 the points part is thrown away, so every new dimension silently",
            "falls back to the global default instead.",
            "With this ON, the configured value is written when the dimension is first created.",
            "Set it up in SRP's config first; this only makes that setting stick.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Apply Starting Points")
    public boolean fixStartingPoints = false;

    @Config.Comment({
            "Lets you set a different parasite population cap per dimension.",
            "SRP's cap is a single global number, so a parasite dimension and your home dimension",
            "have to share it. With this ON, the dimensions you list below get their own multiplier.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Enable Per-Dimension Mob Cap")
    public boolean enablePerDimMobCap = false;

    @Config.Comment({
            "One entry per line, written as 'dimension=multiplier'.",
            "Below 1 lowers the cap, above 1 raises it. For example '111=0.75' gives dimension 111",
            "a quarter fewer parasites than the global cap allows.",
            "Only used when the option above is ON. Dimensions you do not list are unaffected.",
            "Bad entries are ignored. No restart needed."
    })
    @Config.Name("Per-Dimension Mob Cap Multipliers")
    public String[] perDimMobCapMultipliers = new String[] { "111=0.75" };

    @Config.Comment({
            "Required if you run a mod that ticks entities on more than one thread (EntityThreading).",
            "SRP keeps each dimension's evolution points and phase in lists that are not safe to write",
            "from two threads at once, so a parasite ticked on a worker thread can corrupt them - your",
            "world ends up with wrong phases or points that jump around.",
            "With this ON, such writes are handed back to the main thread and land a fraction of a",
            "second later instead of corrupting anything.",
            "Turn this ON before unlocking parasites in more than one dimension while a threading mod",
            "is installed. Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: SaveData Thread Safety")
    public boolean fixSaveDataThreadSafety = false;

    @Config.Comment({
            "The companion to the fix above, for the moment SRP's save data is first created.",
            "That code is not thread-safe either: two threads can create it at the same time, which",
            "either loses every point written into the discarded copy, or corrupts the world's save-data",
            "list badly enough to cut the world save short.",
            "With this ON that step runs one thread at a time. Nothing else changes.",
            "Turn it on together with the fix above if you run a threading mod.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: SaveData Get Race")
    public boolean fixSaveDataGetRace = false;

    @Config.Comment({
            "Makes the Needler effect actually do something to players.",
            "When Needler reaches its final stage it is supposed to hit for a share of your maximum",
            "health and, if that kills you, give a totem of undying its chance to save you. On players",
            "that never happens: SRP looks up the target's entity id, players do not have one, and the",
            "resulting error aborts the whole thing - but only AFTER the effect has already been used",
            "up. So the effect quietly comes off you for free, and the log fills with 'Problem with",
            "needler and an entity'.",
            "With this ON players are treated as 'minecraft:player' and the final stage plays out.",
            "WARNING - this MAKES THE GAME HARDER. You will start taking damage you were not taking",
            "before. Tune it with SRP's own Needler Damage / Needler Maximum Damage Player / Needler",
            "Terminal Amplifier, or put 'minecraft:player' in SRP's Needler Immune Mob List to switch",
            "it back off for players only.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Needler Works On Players")
    public boolean fixNeedlerOnPlayers = false;

    @Config.Comment({
            "Makes 'Meteor Blacklisted Dimensions' keep working after you reload the world.",
            "SRP decides once per dimension whether meteors and infestation anchors apply there, but",
            "when it saves that decision it writes down the wrong value - a setting from the",
            "world-creation screen instead of the dimension's own. Reloading reads that wrong value",
            "back, so from your second visit onwards every dimension behaves as if it were blacklisted.",
            "Two things break, both by opening up rather than shutting down: the meteor can never fall",
            "again, and - worse - the check for 'is this spot near an infestation source' starts",
            "answering yes everywhere, so parasites spawn all over the world instead of around their",
            "anchors. That is what makes them seem to follow you.",
            "With this ON the correct value is saved and the setting survives a reload.",
            "This does NOT repair a world that has already been saved - the wrong value is in the file",
            "and SRP only makes the decision once, when a dimension is first visited. Use a new world.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Meteor Trigger Persistence")
    public boolean fixMeteorTriggerPersistence = false;

    @Config.Comment({
            "Makes parasites from SRP ADD-ONS stay near infestation sources, like the base mod's own do.",
            "Scape and Run: Parasites only lets parasites spawn near an infestation source, which is what",
            "keeps outbreaks local instead of covering the map. Add-ons such as SRPExtra and",
            "SRPDeepSeaDanger register their mobs with the ordinary game spawner instead, so that rule",
            "never applies to them - which is why aquatic parasites turn up in every body of water on the",
            "map, thousands of blocks from any outbreak, while dry land at the same distance stays empty.",
            "With this ON an add-on parasite is only allowed to spawn naturally where a base-mod parasite",
            "would be. Mob spawners and spawn eggs are unaffected, and the base mod's own parasites are",
            "left exactly as they were.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Addon Parasites Respect Origins")
    public boolean addonParasitesRespectOrigins = false;

    @Config.Comment({
            "Stops item tooltips going missing in JEI/HEI because of the bestiary's screen-distortion",
            "effect.",
            "That effect looks for nearby parasites by walking the list of loaded entities, and JEI/HEI",
            "rebuilds its tooltips on a background thread. When the two happen at once the check throws,",
            "the tooltip is lost, and a ConcurrentModificationException lands in the log.",
            "With this ON the background thread reads its own copy of the list, so it cannot be",
            "disturbed halfway through. Nothing changes on screen - the distortion still looks and",
            "behaves exactly the same.",
            "Client-side only. Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Distortion Tooltip Crash")
    public boolean fixDistortionTooltipCrash = false;

    @Config.Comment({
            "Decides where the parasite meteor lands, instead of dropping it on top of whoever is",
            "outdoors at the time.",
            "Unmodified SRP aims the meteor at a player and offsets it by a random amount that its own",
            "config caps at 229 blocks - close enough to flatten a base, and the crash carves terrain,",
            "spawns parasites and plants an infestation anchor where it lands. There is no warning and",
            "no way to move it further out, because the offset is clamped in code as well as in config.",
            "With this ON the impact point is chosen here: at least 'Meteor Minimum Distance' away, and",
            "never inside a protected area (see the two options below). The meteor still falls, still",
            "does the same damage and still creates the same outbreak - it just lands somewhere you can",
            "walk to rather than somewhere you live.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Meteor Placement Guard")
    public boolean meteorPlacementGuard = false;

    @Config.Comment({
            "How far away, in blocks, the meteor must land at the very least.",
            "SRP's own limit tops out at 229; this replaces it and is not capped by SRP's config.",
            "Only used when 'Meteor Placement Guard' is ON. No restart needed."
    })
    @Config.Name("Meteor Minimum Distance")
    @Config.RangeInt(min = 0, max = 8192)
    public int meteorMinDistance = 250;

    @Config.Comment({
            "How much further out than the minimum the meteor may land, chosen at random.",
            "250 minimum with 350 spread means somewhere between 250 and 600 blocks away.",
            "Only used when 'Meteor Placement Guard' is ON. No restart needed."
    })
    @Config.Name("Meteor Distance Spread")
    @Config.RangeInt(min = 1, max = 8192)
    public int meteorDistanceSpread = 350;

    @Config.Comment({
            "How wide a berth the meteor gives anything it is told to keep away from.",
            "Applies to your spawn point and bed (see below) and to any area another mod has claimed",
            "as protected - InsaneTweaks registers its sanctuaries here, for example.",
            "The crash itself is roughly 25 blocks across, so anything above about 64 leaves real room.",
            "Only used when 'Meteor Placement Guard' is ON. No restart needed."
    })
    @Config.Name("Meteor Protected Radius")
    @Config.RangeInt(min = 0, max = 1024)
    public int meteorProtectedRadius = 96;

    @Config.Comment({
            "Keeps the meteor away from the world spawn and from every bed players have slept in.",
            "This is the layer that works without any setup at all - you do not have to mark anything",
            "for your first base to be safe.",
            "Only used when 'Meteor Placement Guard' is ON. No restart needed. Default ON."
    })
    @Config.Name("Meteor Protect Spawnpoint")
    public boolean meteorProtectSpawnpoint = true;

    @Config.Comment({
            "How far to the side the meteor starts, so it comes down at an angle instead of straight",
            "over the impact point. Purely cosmetic - but a meteor travels in a straight line, so the",
            "further out it starts the more it can clip a hillside on the way in and land short.",
            "0 makes it fall vertically and land exactly where it was aimed. No restart needed."
    })
    @Config.Name("Meteor Entry Offset")
    @Config.RangeInt(min = 0, max = 256)
    public int meteorEntryOffset = 32;

    @Config.Comment({
            "Tells everyone in the world, in chat, where an infestation anchor has just been created,",
            "with the exact coordinates and the direction and distance from where they are standing.",
            "Without this the only clue is the impact sound, which SRP ships no sound file for.",
            "Independent of 'Meteor Placement Guard' - the message is sent wherever the meteor lands.",
            "Takes effect immediately, no restart. Default ON."
    })
    @Config.Name("Meteor Announce In Chat")
    public boolean meteorAnnounceInChat = true;

    @Config.Comment({
            "Also drops a JourneyMap waypoint on the impact point for every player in that world.",
            "Needs JourneyMap 6.0.2 or newer, which added the server-side waypoint API this uses.",
            "Silently does nothing without it - the chat message above is unaffected either way.",
            "Takes effect immediately, no restart. Default ON."
    })
    @Config.Name("Meteor Waypoint")
    public boolean meteorWaypoint = true;

    @Config.Comment({
            "Performance: stop SRP doing expensive bookkeeping for point updates it is going to throw",
            "away anyway.",
            "Infestation blocks ask SRP to add evolution points on nearly every block tick - roughly a",
            "thousand times a second in a developed infestation - and in a dimension where parasites",
            "cannot gain points those requests are all rejected, but only after the work is done.",
            "With this ON the rejection happens first. The result is identical to unmodified SRP,",
            "it just costs less.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Perf: Early Reject SetTotalKills")
    public boolean perfEarlyRejectSetTotalKills = false;

    @Config.Comment({
            "Slows down how fast parasite infestation spreads across blocks, and cuts the server load",
            "it causes by the same amount. In a heavily infested world this is one of the biggest tick",
            "costs in the game.",
            "2 = half speed, 4 = quarter speed, and so on. 1 = untouched SRP behaviour.",
            "The creep still spreads and still looks the same, it just takes longer - which is usually",
            "what you want rather than turning it off.",
            "Takes effect immediately, no restart. Default 1."
    })
    @Config.Name("Perf: Infestation Spread Throttle Divisor")
    @Config.RangeInt(min = 1, max = 64)
    public int spreadThrottleDivisor = 1;
}
