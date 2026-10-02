package com.spege.tombtweaks.platform;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Trzy kategorie, plik config/tombtweaks-common.toml.
 *
 * <p>Bez master switcha: przy trzech featurach z wlasnymi przelacznikami byl juz tylko
 * dodatkowym stanem, w ktorym da sie utknac.
 *
 * <p>Domyslne listy ochrony sa PUSTE. Te z 1.12.2 naleza do konkretnego packa,
 * a publiczny mod nie ma prawa ich zakladac.
 */
public final class Config {

    public final ForgeConfigSpec.BooleanValue restoreEnabled;
    public final ForgeConfigSpec.BooleanValue restoreDebugLogging;

    public final ForgeConfigSpec.BooleanValue decayEnabled;
    public final ForgeConfigSpec.IntValue decayStartTicks;
    public final ForgeConfigSpec.IntValue decayIntervalTicks;
    public final ForgeConfigSpec.IntValue decayMaxHistory;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItems;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItemPrefixes;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedEnchantments;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedNbtStrings;
    public final ForgeConfigSpec.BooleanValue decayProtectedNeverDecay;

    public final ForgeConfigSpec.BooleanValue cooldownEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> cooldownBooks;

    private Config(ForgeConfigSpec.Builder builder) {
        builder.comment("Items return to the slots they came from when a grave is recovered.")
               .push("restore");
        restoreEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: Tombstone's own per-player 'reverse inventory sorting' preference",
                         "runs AFTER this and rewrites the whole inventory order. If a player has it",
                         "on, their restored layout will come back reversed.")
                .define("enabled", true);
        restoreDebugLogging = builder
                .comment("Log what each death recorded and what each recovered grave put back.")
                .define("debugLogging", false);
        builder.pop();

        builder.comment("An unvisited grave slowly drops its contents on the ground.")
               .push("decay");
        decayEnabled = builder
                .comment("Off by default. Read live - no restart needed.")
                .define("enabled", false);
        decayStartTicks = builder
                .comment("Ticks the grave must have been LOADED before decay starts.",
                         "This is Tombstone's own countTicks, so it does not advance while the",
                         "chunk is unloaded. A later death merged into the same grave resets it.",
                         "Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
                .defineInRange("startTicks", 6000, 0, Integer.MAX_VALUE);
        decayIntervalTicks = builder
                .comment("Ticks between each item removal. 1200 = 60 seconds. 0 disables decay.")
                .defineInRange("intervalTicks", 1200, 0, Integer.MAX_VALUE);
        decayMaxHistory = builder
                .comment("Max number of decay records kept per player (item id + count each).")
                .defineInRange("maxHistory", 10, 0, 100);
        decayProtectedItems = builder
                .comment("Full registry names that decay must not eat, e.g. \"mymod:relic_blade\".",
                         "An empty list protects NOTHING.")
                .defineList("protectedItems", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedItemPrefixes = builder
                .comment("Registry-name prefixes, e.g. \"mymod:relic_\".")
                .defineList("protectedItemPrefixes", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedEnchantments = builder
                .comment("Registry names of enchantments that protect whatever carries them.")
                .defineList("protectedEnchantments", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedNbtStrings = builder
                .comment("Top-level string NBT tags, as \"key=value\". A malformed entry is ignored.")
                .defineList("protectedNbtStrings", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedNeverDecay = builder
                .comment("true  = protection is an exemption; a grave holding only protected items stops decaying.",
                         "false = protection is only an ordering; protected items are eaten last.")
                .define("protectedNeverDecay", true);
        builder.pop();

        builder.comment("Cooldowns for Tombstone's magic books.")
               .push("cooldown");
        cooldownEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: the mixins are applied regardless of this flag; it is an early return",
                         "inside the handler, not a gate on mixin application.")
                .define("enabled", true);
        cooldownBooks = builder
                .comment("One entry per book, as \"registry_name;minutes\".",
                         "Minutes are clamped to 720 (12 h). 0 or a malformed entry means no cooldown.",
                         "Works for any of Tombstone's seven tombstone:book_of_* items, not just the",
                         "two below. Other soul-consuming items (scrolls, tablets) are ignored.")
                .defineList("books", Arrays.asList(
                        "tombstone:book_of_disenchantment;6",
                        "tombstone:book_of_magic_impregnation;6"), o -> o instanceof String);
        builder.pop();
    }

    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
