package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Sentient Codex enchantment (native port of UniqueEnchantments' Grimoire,
 * which only ships on 1.16.5). Sentient Codex dynamically raises the level of every other
 * enchantment on the item as the holder's XP level grows.
 *
 * <p>The master on/off switch is {@code ModConfig.modules.enableSentientCodex} (gates the
 * {@code RegistryEvent.Register<Enchantment>} + the Forge-bus handler, hence
 * {@code @Config.RequiresMcRestart} on that flag). The fields below are read live at
 * runtime by {@code SentientCodexHandler}/{@code EnchantmentSentientCodex}, so most need no restart.
 *
 * <p>Boost formula (UE base, with our progression-rate slowdown folded into the XP term):
 * {@code boost = max(0, floor( ln((Start Level + xpLevel * Progression Rate) * 2) * Level Scaling - Step Skip ))}.
 * The resulting boost is then applied per enchantment and clamped to
 * {@code enchantment.getMaxLevel() + Max Levels Above Cap} (works for non-vanilla enchantments too).
 */
public class SentientCodexCategory {

    @Config.Comment({
            "Master live toggle for the Sentient Codex growth EFFECT. When OFF the handler FREEZES: it",
            "stops adding levels and owner-binding goes idle. Levels already granted stay baked in (no",
            "base snapshot is kept to restore). The enchantment stays registered. Read live (no restart).",
            "To fully remove/unregister the enchantment instead, use modules.enableSentientCodex (restart)."
    })
    @Config.Name("Enchant Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Maximum enchantment level of Sentient Codex itself (UE ships max 5, unified to 1 here).",
            "Read at registration - change requires a MC restart to affect the registered enchantment."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 1;

    @Config.Comment({
            "Slows how fast the boost climbs with the holder's XP by scaling the XP term inside the",
            "formula. 1.0 = UE speed; 0.3 = ~70% slower (the same boost needs ~3.3x the XP level).",
            "Does NOT touch the log slope, so the boost still reaches the cap - it just takes longer.",
            "Read live (no restart)."
    })
    @Config.Name("Progression Rate")
    @Config.RangeDouble(min = 0.01, max = 10.0)
    public double progressionRate = 0.3;

    @Config.Comment({
            "How many levels ABOVE each enchantment's own max level the boost may push it. Vanilla",
            "Sharpness caps at V, so with 2 the boosted ceiling is Sharpness VII. Applied per enchantment",
            "using enchantment.getMaxLevel(), so non-vanilla enchantments are capped at their own max + this.",
            "Read live (no restart)."
    })
    @Config.Name("Max Levels Above Cap")
    @Config.RangeInt(min = 0, max = 10)
    public int maxLevelsAboveCap = 2;

    @Config.Comment({
            "UE START_LEVEL constant in the boost formula. Higher = larger baseline inside the log,",
            "so the boost ramps up sooner. Read live (no restart)."
    })
    @Config.Name("Start Level")
    @Config.RangeDouble(min = 0.0, max = 10000.0)
    public double startLevel = 50.0;

    @Config.Comment({
            "UE LEVEL_SCALING constant - multiplies the natural log before Step Skip is subtracted.",
            "Higher = faster growth of the boost. Read live (no restart)."
    })
    @Config.Name("Level Scaling")
    @Config.RangeDouble(min = 0.0, max = 100.0)
    public double levelScaling = 0.9;

    @Config.Comment({
            "UE STEP_SKIP constant - flat amount subtracted after scaling, delaying the first boost.",
            "Higher = the boost stays at 0 until higher XP levels. Read live (no restart)."
    })
    @Config.Name("Step Skip")
    @Config.RangeDouble(min = 0.0, max = 100.0)
    public double stepSkip = 5.0;

    @Config.Comment({
            "How often (in ticks) the handler recomputes the boost and re-applies owner-binding.",
            "20 = once per second. Lower = snappier but more work. Read live (no restart)."
    })
    @Config.Name("Tick Interval")
    @Config.RangeInt(min = 1, max = 200)
    public int tickInterval = 20;

    @Config.Comment({
            "When ON, the first player to hold a Sentient Codex item binds to it (UUID stored in NBT).",
            "Any OTHER player holding it takes 'Binding Damage' every tick interval. Read live (no restart)."
    })
    @Config.Name("Owner Binding")
    public boolean ownerBinding = true;

    @Config.Comment({
            "Void (OUT_OF_WORLD) damage dealt each tick interval to a non-owner holding a bound",
            "Sentient Codex item. Only applies when 'Owner Binding' is ON. Read live (no restart)."
    })
    @Config.Name("Binding Damage")
    @Config.RangeDouble(min = 0.0, max = 1000.0)
    public double bindingDamage = 1.0;

    @Config.Comment({
            "When ON, a Sentient Codex item carries the 'Ashen Legacy' property: once dropped it is routed",
            "through the hardened EntityItemIndestructible (immune to fire, lava, cactus and explosions)",
            "and lingers far longer than ordinary gear, and shows the Ashen Legacy tooltip line. This",
            "reuses the same drop-protection our legendary Living/Sentient gear uses. Read live (no restart)."
    })
    @Config.Name("Confer Ashen Legacy")
    public boolean conferAshenLegacy = true;

    @Config.Comment({
            "When ON, an item that already carries Sentient Codex cannot be further modified on an anvil.",
            "Applying the Sentient Codex book onto a clean item is still allowed. Read live (no restart)."
    })
    @Config.Name("Block Anvil Modification")
    public boolean blockAnvil = true;

    @Config.Comment({
            "Enchantments (by registry name) that Sentient Codex does NOT boost. UE excludes fortune,",
            "efficiency, looting, mending and silk_touch. Read live (no restart)."
    })
    @Config.Name("Excluded Enchantments")
    public String[] excluded = new String[] {
            "minecraft:fortune", "minecraft:efficiency", "minecraft:looting",
            /* "minecraft:mending", */ "minecraft:silk_touch"
    };
}
