package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Bountiful Baubles trinkets that other mods in the pack silently disabled.
 *
 * <p>Bountiful Baubles implements its trinkets as plain Forge event listeners. That works in a
 * vanilla-ish pack and stops working the moment another mod takes ownership of the same pipeline.
 * This category is where those trinkets get put back, one flag each, so that removing the
 * offending mod and switching the flag off restores stock behaviour instead of double-applying it.
 *
 * <p>The Cross Necklace has its own home in {@code whtCompat} — it was broken by WorseHurtTimer,
 * not by FirstAid, and its repair is an invincibility-frame multiplier rather than a death hook.
 */
public class BbCompatCategory {

    @Config.Comment({
            "Master switch for the Bountiful Baubles trinket repairs below.",
            "OFF leaves every Bountiful Baubles item exactly as the mod ships it.",
            "Does nothing unless Bountiful Baubles is installed. Read live, no restart needed.",
            "Default ON."
    })
    @Config.Name("Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Make the Broken Heart trinket (bountifulbaubles:trinketbrokenheart) save the wearer",
            "from lethal damage again, at the cost of max health.",
            "",
            "Why this exists: Bountiful Baubles drives the trinket from LivingDamageEvent, and",
            "FirstAid cancels LivingHurtEvent for every real player at priority LOWEST. A cancelled",
            "hurt event makes ForgeHooks.onLivingHurt return 0, EntityPlayer.damageEntity returns",
            "early, and LivingDamageEvent is therefore never posted for a player at all. The",
            "trinket is 100% inert in this pack, not merely weakened.",
            "",
            "The repair hooks EntityLivingBase.checkTotemDeathProtection instead - the one death",
            "hook FirstAid calls on purpose (CommonUtils.killPlayer, gated on FirstAid's own",
            "externalhealing.allowOtherHealingItems, which must stay true for this to work).",
            "FirstAid then restores every death-causing body part to 1 HP by itself, which is",
            "exactly the 'survive on 1 HP' outcome the trinket was written for.",
            "",
            "Only armed when both Bountiful Baubles and FirstAid are present: without FirstAid the",
            "mod's own handler works and this would fire on top of it. Default ON."
    })
    @Config.Name("Broken Heart: Enabled")
    public boolean brokenHeartEnabled = true;

    @Config.Comment({
            "Max health destroyed by each save, in half-hearts. 2.0 = one full heart container,",
            "which is what the trinket's tooltip promises.",
            "Sleeping gives it back - that part still works, because Bountiful Baubles clears the",
            "modifier on PlayerWakeUpEvent and this repair deliberately reuses the mod's own",
            "modifier UUID. Set regenheartcontainers=false in Bountiful Baubles' config to make",
            "the loss last until death instead.",
            "Default 2.0."
    })
    @Config.Name("Broken Heart: Max Health Cost")
    @Config.RangeDouble(min = 0.0D, max = 20.0D)
    public double brokenHeartMaxHealthCost = 2.0D;

    @Config.Comment({
            "The trinket refuses to save you when doing so would drop your max health below this.",
            "This is what stops it from being an infinite death ward and is the reason it can run",
            "out: at the default cost of 2.0 a 20-max-health player gets seven saves before the",
            "eighth lethal hit kills them for real.",
            "Also a safety floor - FirstAid scales its body-part pools from max health, so a player",
            "ground down to almost nothing would be one-shot by everything.",
            "Default 6.0 (three hearts)."
    })
    @Config.Name("Broken Heart: Minimum Max Health")
    @Config.RangeDouble(min = 1.0D, max = 20.0D)
    public double brokenHeartMinMaxHealth = 6.0D;

    @Config.Comment({
            "Seconds before the trinket can save the same player again.",
            "Without this, standing in lava or burning to death spends every remaining heart",
            "container within a second or two and the player still dies - the saves are handed out",
            "faster than anyone can walk out of the damage. The cooldown makes the trinket buy one",
            "escape rather than a fistful of wasted hearts.",
            "0 removes the limit and reproduces Bountiful Baubles' own unthrottled behaviour.",
            "Default 10."
    })
    @Config.Name("Broken Heart: Cooldown Seconds")
    @Config.RangeInt(min = 0, max = 600)
    public int brokenHeartCooldownSeconds = 10;

    @Config.Comment({
            "Log one line per save (who, what killed them, what it cost, what is left).",
            "Cheap - it can only fire on a death - but off by default so it stays out of the log.",
            "Default OFF."
    })
    @Config.Name("Broken Heart: Log Saves")
    public boolean brokenHeartLogSaves = false;
}
