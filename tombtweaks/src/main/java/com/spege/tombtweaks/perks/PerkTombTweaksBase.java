package com.spege.tombtweaks.perks;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import ovh.corail.tombstone.api.capability.Perk;

/**
 * Shared plumbing for this mod's Corail Tombstone perks: level cap, point cost and the
 * enabled/disabled state all come from config.
 *
 * <p>Two things about {@link Perk} shape the subclasses:
 * <ul>
 *   <li>{@code getTranslationKey()} is {@code final} and hardcodes the {@code tombstone.perk.}
 *       prefix, so our lang keys live under Tombstone's namespace in <i>our</i> lang file.
 *       Overriding {@code getDescription()} would not help — the GUI builds its tooltip from
 *       the key directly.</li>
 *   <li>{@code getCost(int)} defaults to a flat 1 point per level and no native perk overrides
 *       it. Making it configurable here is the cheapest real balance lever we have.</li>
 * </ul>
 */
public abstract class PerkTombTweaksBase extends Perk {

    protected PerkTombTweaksBase(String name, ResourceLocation icon) {
        super(name, icon);
    }

    /** Configured level ceiling. 0 disables the perk outright. */
    protected abstract int configMaxLevel();

    /** Configured perk-point price of each level. */
    protected abstract int configPointCost();

    /** Configured master switch, plus any mod-presence requirement. */
    protected abstract boolean configEnabled();

    /**
     * {@code Perk} satisfies {@code IStringSerializable} with the SRG-named {@code func_176610_l},
     * which the deobfuscated Tombstone jar does not present under its MCP name — so the compiler
     * sees {@code getName()} as still abstract. Implementing it here is correct either way:
     * reobfuscation maps this straight back onto {@code func_176610_l}.
     */
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getLevelMax() {
        return configMaxLevel();
    }

    @Override
    public int getCost(int level) {
        return level > 0 ? configPointCost() : 0;
    }

    /**
     * Greying the perk out is how a config flag disables it — never by skipping registration.
     *
     * <p>Be aware Tombstone strips disabled perks from a player's map and refunds the points,
     * so flipping this off mid-world erases invested levels rather than parking them.
     */
    @Override
    public boolean isDisabled(@Nullable EntityPlayer player) {
        return !configEnabled() || configMaxLevel() <= 0;
    }

    /** Formats a percentage without a trailing ".0", e.g. "6" and "7.5". */
    protected static String formatPercent(double fraction) {
        double percent = fraction * 100.0D;
        String text = String.format(java.util.Locale.ROOT, "%.1f", Double.valueOf(percent));
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /**
     * The literal percent sign to put in a bonus line: {@code "%%"}, not {@code "%"}.
     *
     * <p>{@code ScreenKnowledge} does not render a bonus component directly. It resolves it with
     * {@code getFormattedText()} and then feeds the resulting <i>text</i> back into
     * {@code I18n.format} as though it were a translation key
     * ({@code LangKey.createText(ITextComponent, Object...)}). The lookup misses and returns the
     * string unchanged, but {@code String.format} still runs over it — so a lone {@code %} is read
     * as a format specifier and the whole line comes out as "Format error: ...".
     *
     * <p>Tombstone's own perks work around this the same way; see {@code PerkAlchemist} and
     * {@code PerkTreasureSeeker}, which both build their bonus with {@code "%%"}. Following the
     * house idiom rather than inventing another is what keeps our lines rendering like theirs.
     */
    protected static final String PERCENT = "%%";
}
