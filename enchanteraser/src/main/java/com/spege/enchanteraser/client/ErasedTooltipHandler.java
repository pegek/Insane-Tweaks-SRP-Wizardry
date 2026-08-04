package com.spege.enchanteraser.client;

import java.util.List;
import java.util.Map;

import com.spege.enchanteraser.EnchantEraser;
import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Marks an erased enchantment that is still sitting in an item's NBT, so a player can tell at a glance
 * that it does nothing and can no longer be obtained.
 *
 * <p>An event, not a mixin, and deliberately so. Both vanilla paths add the enchantment line as
 * <b>exactly</b> {@code enchantment.getTranslatedName(level)}, with no colour prefix and nothing
 * concatenated — {@code ItemStack.getTooltip} for a normal item, {@code ItemEnchantedBook.addInformation}
 * for a book — so recomputing that same value and matching the line is enough.
 *
 * <p>🚨 Do not mixin {@code Enchantment.getTranslatedName} ({@code func_77316_c}) instead. It is the
 * same trap as the main mechanism: the method is virtual and SoManyEnchantments' {@code EnchantmentBase}
 * overrides it, so an injection into the base class would be skipped for every one of its enchantments.
 * Matching on the string returned by a <em>virtual</em> call is immune to that by construction — we get
 * back the identical value vanilla just inserted, overridden implementation included.
 *
 * <p>The enchantment's own name is kept readable rather than struck through: a curse's name already
 * carries {@code §c}, and any colour code cancels {@code §m}, so a strikethrough would break halfway
 * along the line. The player should be able to see <em>what</em> they have, not just that it is dead.
 *
 * <p>Side safety: {@code @Mod.EventBusSubscriber(value = Side.CLIENT)} is checked by FML <b>before</b>
 * {@code Class.forName}, so this class never loads on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = EnchantEraser.MODID, value = Side.CLIENT)
public final class ErasedTooltipHandler {

    private static final String LANG_KEY = "enchanteraser.tooltip.erased";

    /**
     * House style for a suffix the config supplies as plain text: yellow, then italic. The order
     * matters — a colour code resets formatting in Minecraft, so {@code §o§e} would render upright.
     */
    private static final String DEFAULT_STYLE = TextFormatting.YELLOW.toString() + TextFormatting.ITALIC;

    private ErasedTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!EnchantEraserConfig.markErasedInTooltip || EraserState.isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> erased = EraserState.disabledOn(event.getItemStack());
        if (erased.isEmpty()) {
            return;
        }
        List<String> lines = event.getToolTip();
        String suffix = suffix();
        for (Map.Entry<Enchantment, Integer> entry : erased.entrySet()) {
            String name = entry.getKey().getTranslatedName(entry.getValue().intValue());
            markLine(lines, name, suffix);
        }
    }

    /**
     * Config text wins; an empty setting falls back to the translated default.
     *
     * <p>Plain config text is styled for the author — writing {@code erased} in the config should not
     * produce a line indistinguishable from a real enchantment. A suffix that brings its own
     * {@code §} codes is passed through untouched, so the styling stays fully overridable.
     */
    private static String suffix() {
        String configured = EnchantEraserConfig.erasedTooltipSuffix;
        if (configured != null && !configured.trim().isEmpty()) {
            String trimmed = configured.trim();
            return trimmed.indexOf('§') >= 0 ? trimmed : DEFAULT_STYLE + trimmed;
        }
        // The client-side I18n, which is correct here and non-deprecated. Safe despite living in
        // net.minecraft.client: FML checks Side.CLIENT above before this class is ever loaded.
        return I18n.format(LANG_KEY);
    }

    /**
     * Append the marker to the one line that is this enchantment. Exact match first, because that is
     * what both vanilla paths produce; {@code contains} is the fallback for a mod that wrapped the line
     * in its own colour code. Stops at the first hit — the same enchantment appears once per stack.
     */
    private static void markLine(List<String> lines, String name, String suffix) {
        for (int i = 0; i < lines.size(); i++) {
            if (name.equals(lines.get(i))) {
                lines.set(i, lines.get(i) + " " + suffix);
                return;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.contains(name) && !line.contains(suffix)) {
                lines.set(i, line + " " + suffix);
                return;
            }
        }
    }
}
