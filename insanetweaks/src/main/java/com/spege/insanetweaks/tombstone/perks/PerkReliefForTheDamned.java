package com.spege.insanetweaks.tombstone.perks;

import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.TombstoneCategory.ReliefForTheDamnedConfig;

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
 * redirect the three {@code EnigmaticConfigs} constants where the mod reads them. The icon
 * points straight at the ring's own texture rather than copying it into our assets — safe,
 * because the perk is disabled whenever Enigmatic Legacy is missing.
 */
public class PerkReliefForTheDamned extends PerkInsaneTweaksBase {

    public PerkReliefForTheDamned() {
        super("relief_for_the_damned",
                new ResourceLocation("enigmaticlegacy", "textures/items/cursed_ring.png"));
    }

    private static ReliefForTheDamnedConfig cfg() {
        return ModConfig.tombstone.reliefForTheDamned;
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
        return ModConfig.tombstone.enableTombstoneTweaks
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
                com.spege.insanetweaks.util.CurseReliefHelper.reductionAtLevel(level));
        return Collections.<ITextComponent>singletonList(
                new TextComponentString("-" + amount + "% ")
                        .appendSibling(new TextComponentTranslation(getTranslationKey() + ".bonus")));
    }
}
