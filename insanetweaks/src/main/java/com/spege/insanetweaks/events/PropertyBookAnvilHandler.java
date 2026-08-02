package com.spege.insanetweaks.events;

import com.spege.insanetweaks.api.AdvPropertyRegistry.Property;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.PropertyBookItem;
import com.spege.insanetweaks.util.AdvPropertyResolver;
import com.spege.insanetweaks.util.AdvPropertyStorage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The anvil recipe behind Property Books: tool in the left slot, book in the right, out comes the
 * same tool carrying that property.
 *
 * <h3>Priority</h3>
 * {@code HIGHEST}, for one specific reason: {@code AnvilUpdateEvent} is {@code @Cancelable}, and
 * cancelling it is terminal for the output. {@code SentientCodexHandler.onAnvil} cancels at
 * {@code NORMAL} for <i>any</i> left-hand stack carrying Sentient Codex, which would otherwise
 * throw away our output before anyone saw it - so we need the first word, and that handler now
 * steps aside when it sees one of these books. {@code EnchantGrantAnvilHandler} sits at
 * {@code LOWEST} and only ever cancels ungranted gated enchant books, so it is inert here.
 *
 * <p>Running first is safe rather than greedy: the recognition test requires our own item in the
 * right slot, so there is no pair another mod could plausibly want that we could steal.
 *
 * <h3>Why an event and not a mixin</h3>
 * Four mods contend for {@code ContainerRepair.updateRepairOutput} in this pack, and NoExpensive
 * {@code @Overwrite}s the whole method - which is why both of UniversalTweaks' anvil mixins are
 * dead. What makes the Forge route survive that is that NoExpensive's replacement still constructs
 * and posts {@code AnvilUpdateEvent} itself (confirmed in its bytecode), so this handler is
 * reached normally. A mixin here would have had to fight the overwrite.
 */
public class PropertyBookAnvilHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || left.getCount() != 1) {
            return;
        }
        if (!(right.getItem() instanceof PropertyBookItem)) {
            return;
        }

        // A blank book, or one naming a property that is no longer registered, grants nothing.
        Property property = PropertyBookItem.getProperty(right);
        if (property == null || !property.canApplyTo(left)) {
            return;
        }

        // Resolver, not AdvPropertyStorage: an item that already has this from its own class or
        // from a Sentient Codex would show no change, so the anvil would silently eat the book
        // and the levels for nothing.
        if (!ModConfig.propertyBooks.allowRedundantGrants
                && AdvPropertyResolver.has(left, property.id)) {
            return;
        }

        ItemStack output = left.copy();
        if (!AdvPropertyStorage.add(output, property.id)) {
            return;
        }

        // Carry the rename field through. Setting an output bypasses vanilla's own naming pass, so
        // without this a player renaming an item while applying a book would lose the rename.
        String name = event.getName();
        if (name != null && !name.isEmpty()) {
            if (!name.equals(left.getDisplayName())) {
                output.setStackDisplayName(name);
            }
        } else if (left.hasDisplayName()) {
            output.clearCustomName();
        }

        event.setOutput(output);
        event.setCost(ModConfig.propertyBooks.anvilLevelCost);
        event.setMaterialCost(1);
    }
}
