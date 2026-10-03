package com.spege.ebreduxaddon.feature.item;

import com.binaris.wizardry.content.item.WandItem;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Sentient Wand: forma po ewolucji Symbiotic Wand, tier master, stale bonusy z configu. */
public class SentientWandItem extends WandItem {

    public SentientWandItem() {
        super(SpellTiers.MASTER, Elements.MAGIC);
    }

    /** Master i tak nie ma wyzszego tieru; nadpisane jawnie, zeby linia nigdy nie wrocila do Redux. */
    @Override
    protected ItemStack applyTierUpgrade(@Nullable Player player, ItemStack wand, ItemStack tomeStack) {
        return wand;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int cost = (int) Math.round((1 - Config.INSTANCE.sentientCostMultiplier.get()) * 100);
        int duration = (int) Math.round((Config.INSTANCE.sentientDurationMultiplier.get() - 1) * 100);
        int potency = (int) Math.round((Config.INSTANCE.sentientPotencyMultiplier.get() - 1) * 100);
        tooltip.add(Component.translatable("item.ebreduxaddon.wand.bonus", cost, duration, potency).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
