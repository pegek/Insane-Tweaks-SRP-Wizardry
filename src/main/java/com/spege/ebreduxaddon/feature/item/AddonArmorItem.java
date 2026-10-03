package com.spege.ebreduxaddon.feature.item;

import com.spege.ebreduxaddon.core.SymbiosisCurve;
import com.spege.ebreduxaddon.platform.ArmorProgress;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Czesc zbroi Grafted albo Sentient. Bonusy do zaklec licza sie w SpellBonus, nie w atrybutach. */
public class AddonArmorItem extends ArmorItem {

    private final boolean sentient;

    public AddonArmorItem(ModArmorMaterials material, Type type) {
        super(material, type, new Properties());
        this.sentient = material == ModArmorMaterials.SENTIENT;
    }

    public boolean isSentient() {
        return sentient;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (sentient) {
            int cost = (int) Math.round(Config.INSTANCE.sentientCostReductionPerPiece.get() * 100);
            int potency = (int) Math.round(Config.INSTANCE.sentientPotencyPerPiece.get() * 100);
            tooltip.add(Component.translatable("item.ebreduxaddon.armor.bonus", cost, potency).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            int cost = (int) Math.round(Config.INSTANCE.graftedCostReductionPerPiece.get() * 100);
            tooltip.add(Component.translatable("item.ebreduxaddon.armor.bonus", cost, 0).withStyle(ChatFormatting.GRAY));
            double absorbed = ArmorProgress.get(stack);
            long evolveAt = Config.INSTANCE.armorEvolveAt.get();
            tooltip.add(Component.translatable("item.ebreduxaddon.grafted.progress", (long) absorbed, evolveAt,
                    (int) Math.floor(SymbiosisCurve.progress((long) absorbed, evolveAt) * 100)).withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable("item.ebreduxaddon.armor.last_stand").withStyle(ChatFormatting.DARK_GRAY));
    }
}
