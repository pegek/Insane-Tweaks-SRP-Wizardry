package com.spege.insanetweaks.events;

import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.EnchantGrantMarker;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Enforces the grant marker at the anvil — the single entrance for quest-gated enchantments.
 *
 * <p>See {@link EnchantGrantMarker} for why the gate is here and not on the (open-ended) list of
 * sources that can produce a book. Short version: the remaining third-party rollers in the pack
 * enumerate the enchantment registry themselves, an idiom we cannot filter without also hiding our
 * enchantments from JEI and the description tooltip. Gating application instead makes every unforeseen
 * source harmless without a line of code per source.
 *
 * <p>{@code AnvilUpdateEvent} is a Forge event fired at the top of {@code updateRepairOutput}, which is
 * deliberately preferable to a mixin here: {@code ContainerRepair} already has UniversalTweaks and
 * noexpensive fighting over it in this pack (their mixins collide in the current log), and the event
 * sidesteps that class entirely. Cancelling yields no output, exactly as if the two items were
 * incompatible.
 *
 * <p>Both slots are checked. The right slot is the usual case (a book being applied); the left matters
 * when two books are combined, which would otherwise launder an unmarked book into a marked-looking
 * result. Gated on {@code enchantments.requireGrantMarker}.
 */
public class EnchantGrantAnvilHandler {

    /**
     * LOWEST so that mods which build a legitimate output run first and we get the final say. We only
     * ever cancel, never produce an output, so nothing downstream is deprived of its chance to act.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!ModConfig.enchantments.requireGrantMarker) {
            return;
        }
        Enchantment blocked = EnchantGrantMarker.findUngranted(event.getRight());
        if (blocked == null) {
            blocked = EnchantGrantMarker.findUngranted(event.getLeft());
        }
        if (blocked != null) {
            event.setCanceled(true);
        }
    }

    /**
     * Tell the player why an unmarked book is refusing to work, instead of letting them wonder why the
     * anvil is silent. Fires client-side only, which is why it can sit on the same handler as the
     * server-side veto without a side check.
     */
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
