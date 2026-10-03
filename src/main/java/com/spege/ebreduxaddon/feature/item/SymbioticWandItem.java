package com.spege.ebreduxaddon.feature.item;

import com.binaris.wizardry.content.item.WandItem;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.spege.ebreduxaddon.core.SymbiosisCurve;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.WandProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Symbiotic Wand: rozdzka Redux tieru advanced, ktora rosnie z mana wydana przez nia na zaklecia
 * i po progu ewoluuje w {@link SentientWandItem} (WandEvolution).
 *
 * <p>🚨 Ulepszenie tieru tomem jest zablokowane. Redux w applyTierUpgrade podmienia stack na
 * RegistryUtils.getWand(nextTier, element), czyli na zwykla rozdzke Redux - to skasowaloby postep
 * i sama linie. Jedyna droga w gore to ewolucja.
 */
public class SymbioticWandItem extends WandItem {

    public SymbioticWandItem() {
        super(SpellTiers.ADVANCED, Elements.MAGIC);
    }

    @Override
    protected ItemStack applyTierUpgrade(@Nullable Player player, ItemStack wand, ItemStack tomeStack) {
        return wand;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        long points = WandProgress.get(stack);
        long evolveAt = Config.INSTANCE.wandEvolveAt.get();
        double p = SymbiosisCurve.progress(points, evolveAt);
        tooltip.add(Component.translatable("item.ebreduxaddon.symbiotic_wand.progress",
                points, evolveAt, (int) Math.floor(p * 100)).withStyle(ChatFormatting.DARK_GREEN));
        int cost = (int) Math.round((1 - SymbiosisCurve.costMultiplier(p, Config.INSTANCE.symbioticMaxCostReduction.get())) * 100);
        int duration = (int) Math.round((SymbiosisCurve.durationMultiplier(p, Config.INSTANCE.symbioticMaxDurationBonus.get()) - 1) * 100);
        tooltip.add(Component.translatable("item.ebreduxaddon.wand.bonus", cost, duration, 0).withStyle(ChatFormatting.GRAY));
    }
}
