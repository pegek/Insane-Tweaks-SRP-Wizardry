package com.spege.insanetweaks.tombstone.perks;

import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.TombstoneCategory.AssimilatedKnowledgeConfig;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * <b>Assimilated Knowledge</b> — high-tier parasite kills feed Tombstone's knowledge economy.
 *
 * <p>Rationale: in this pack knowledge comes only from alignment events (exorcism, prayer,
 * freeing soul receptacles), none of which touch the thing players actually spend their time
 * doing — fighting the hive. This perk wires the Knowledge of Death tree into the core loop.
 *
 * <p>The effect lives in {@code AssimilatedKnowledgeHandler}; Tombstone never calls a perk.
 *
 * <p>The icon reuses the existing Corrupted Fruit texture. Tombstone blits perk icons with
 * {@code drawScaledCustomSizeModalRect(x, y, 0, 0, 64, 64, 16, 16, 64f, 64f)} — the sampled
 * region is the whole texture (64/64 = 1.0) whatever the PNG's real size, so a 16x16 item
 * texture renders correctly and no new asset is needed.
 */
public class PerkAssimilatedKnowledge extends PerkInsaneTweaksBase {

    public PerkAssimilatedKnowledge() {
        super("assimilated_knowledge",
                new ResourceLocation("insanetweaks", "textures/items/corrupted_fruit.png"));
    }

    private static AssimilatedKnowledgeConfig cfg() {
        return ModConfig.tombstone.assimilatedKnowledge;
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
        return ModConfig.tombstone.enableTombstoneTweaks && cfg().enabled;
    }

    /** Chance that a qualifying parasite kill teaches this player something. */
    public static double chanceAtLevel(int level) {
        return Math.min(1.0D, level * cfg().chancePerLevel);
    }

    @Override
    public List<ITextComponent> getCurrentBonusInfo(int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        return Collections.<ITextComponent>singletonList(
                new TextComponentString(formatPercent(chanceAtLevel(level)) + "% ")
                        .appendSibling(new TextComponentTranslation(getTranslationKey() + ".bonus")));
    }
}
