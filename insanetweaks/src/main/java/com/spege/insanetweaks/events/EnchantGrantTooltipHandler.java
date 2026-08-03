package com.spege.insanetweaks.events;

import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.EnchantGrantMarker;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client half of the quest-gate: tells the player why an unmarked book is refusing to work,
 * instead of letting them wonder why the anvil is silent. The server half — the actual veto —
 * lives in {@link EnchantGrantAnvilHandler}.
 *
 * <p>🚨 These two must stay separate classes, and the reason is a gameplay bug rather than a
 * crash. They used to share one class carrying both {@code onAnvilUpdate} and this method. That
 * class mixes sides, so the obvious "make it client-only" reflex — a class-level
 * {@code @SideOnly(Side.CLIENT)} — would have taken the anvil veto down with the tooltip: the
 * dedicated server would start with no error whatsoever and simply stop gating enchantments.
 * Silence in the log is what makes that worse than a crash. Splitting removes the temptation:
 * neither class mixes sides any more, so the annotation is unambiguously correct on this one and
 * unambiguously wrong on the other.
 *
 * <p>The {@code @SideOnly} is safe here only because the registration site in
 * {@code InsaneTweaksMod.init()} sits inside an {@code event.getSide() == Side.CLIENT} block —
 * a class-level annotation makes instantiation fatal on a server, so the two travel together.
 *
 * <p>Registered unconditionally within that block: both config flags are read live here, so
 * turning the tooltip on needs no restart.
 */
@SideOnly(Side.CLIENT)
public class EnchantGrantTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!ModConfig.enchantments.requireGrantMarker || !ModConfig.enchantments.grantMarkerTooltip) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (EnchantGrantMarker.findUngranted(stack) == null) {
            return;
        }
        List<String> tooltip = event.getToolTip();
        tooltip.add(I18n.translateToLocal("tooltip.insanetweaks.enchant.not_granted"));
        tooltip.add(I18n.translateToLocal("tooltip.insanetweaks.enchant.not_granted.hint"));
    }
}
