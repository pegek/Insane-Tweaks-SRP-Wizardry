package com.spege.tombtweaks.perks;

import java.util.Collections;
import java.util.List;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.ReliefForTheDamnedConfig;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Loader;

/**
 * <b>Relief for the Damned</b> — each level waives a share of every penalty carried by
 * Enigmatic Legacy's Ring of the Seven Curses.
 *
 * <p>The ring imposes three separate punishments, and this perk softens all of them by the
 * same fraction of their own value ({@code reductionPerLevel} per level):
 * <ul>
 *   <li>incoming damage multiplier — {@code 1 + (m - 1) * (1 - r)}, since only the surcharge
 *       above 1.0 is a penalty;</li>
 *   <li>armour and armour-toughness debuff — {@code d * (1 - r)};</li>
 *   <li>damage dealt to monsters — {@code d * (1 - r)}.</li>
 * </ul>
 *
 * <p>At the default 12% per level and 3 levels that is 36% off: a diamond sword that hits a
 * zombie for 7 lands 3.5 with the ring, and 4.76 with the ring and a maxed perk.
 *
 * <p>Enigmatic Legacy offers no API for any of this, so the work is done by mixins that
 * redirect the three {@code EnigmaticConfigs} constants where the mod reads them.
 *
 * <p>The icon references one of Enigmatic Legacy's own textures rather than copying it into our
 * assets — safe, because the perk is disabled whenever Enigmatic Legacy is missing. It points at
 * the <b>Blessed</b> Ring, not the Cursed one, for a mechanical reason as much as a thematic one:
 * {@code cursed_ring.png} is a 16x80 five-frame animation strip, and Tombstone blits perk icons
 * with {@code drawScaledCustomSizeModalRect(x, y, 0, 0, 64, 64, 16, 16, 64f, 64f)}, which samples
 * the entire file regardless of its real dimensions. A non-square texture therefore renders as the
 * whole strip crushed into one 16x16 cell. Any icon used here must be square. That the blessed
 * ring is the cursed one's counterpart, and this perk is mercy from the curse, is a bonus.
 */
public class PerkReliefForTheDamned extends PerkTombTweaksBase {

    public PerkReliefForTheDamned() {
        super("relief_for_the_damned",
                new ResourceLocation("enigmaticlegacy", "textures/items/bless_ring.png"));
    }

    private static ReliefForTheDamnedConfig cfg() {
        return TombTweaksConfig.tombstone.reliefForTheDamned;
    }

    @Override
    protected int configMaxLevel() {
        return cfg().maxLevel;
    }

    @Override
    protected int configPointCost() {
        return cfg().pointCostPerLevel;
    }

    @Override
    protected boolean configEnabled() {
        return TombTweaksConfig.tombstone.enableTombstoneTweaks
                && cfg().enabled
                && Loader.isModLoaded("enigmaticlegacy");
    }

    @Override
    public List<ITextComponent> getCurrentBonusInfo(int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        // Shares CurseReliefHelper with the Enigmatic Legacy mixins, so the number shown in the
        // GUI can never drift from the number actually applied to the ring.
        String amount = formatPercent(
                com.spege.tombtweaks.util.CurseReliefHelper.reductionAtLevel(level));
        return Collections.<ITextComponent>singletonList(
                new TextComponentString("-" + amount + PERCENT + " ")
                        .appendSibling(new TextComponentTranslation(getTranslationKey() + ".bonus")));
    }
}
