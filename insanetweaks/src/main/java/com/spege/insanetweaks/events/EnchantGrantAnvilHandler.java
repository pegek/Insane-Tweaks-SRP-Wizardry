package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.EnchantGrantMarker;

import net.minecraft.enchantment.Enchantment;
import net.minecraftforge.event.AnvilUpdateEvent;
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
 *
 * <p>🚨 Server-side mechanic — this class must NEVER get a class-level
 * {@code @SideOnly(Side.CLIENT)}. It used to also carry the explanatory tooltip, which made it a
 * mixed-side class and an inviting target for exactly that annotation; the result would have been
 * a dedicated server that starts cleanly and silently stops gating enchantments. The tooltip now
 * lives in {@link EnchantGrantTooltipHandler}, so there is nothing client-side left here to tempt
 * anyone. Registration stays unconditional in {@code InsaneTweaksMod.init()}.
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
}
