package com.spege.insanetweaks.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentSentientCodex;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The single answer to "which advanced properties does this stack have?".
 *
 * <p>Three independent sources feed it, and every one of them is load-bearing:
 * <ol>
 *   <li><b>The item class</b> - {@link ITweaksPropertyHolder#getActiveAdvProperties}. How our own
 *       gear declares what it is.</li>
 *   <li><b>The stack's NBT</b> - {@link AdvPropertyStorage}. How Property Books grant a property to
 *       one particular item, including vanilla and third-party gear that cannot implement an
 *       interface of ours.</li>
 *   <li><b>An enchantment</b> - Sentient Codex confers Ashen Legacy.</li>
 * </ol>
 *
 * <p>Consolidating these is the point. Before, each consumer re-derived the answer and each got a
 * different one: {@code GlobalPropertyTooltipHandler} tested {@code instanceof
 * ITweaksPropertyHolder} and so could not see source 3 at all, which is precisely why a second,
 * near-duplicate {@code SentientCodexTooltipHandler} had to exist alongside it (its own javadoc
 * said so) - two handlers that had to agree on which one stayed quiet to avoid printing the line
 * twice. Meanwhile {@code LegendaryDropHelper} hand-rolled sources 1 and 3 in its own order.
 * Routing everything through here means a new property or a new source works everywhere at once,
 * and the duplicate handler could be deleted.
 */
public final class AdvPropertyResolver {

    private AdvPropertyResolver() {
    }

    /**
     * Every property id on this stack, de-duplicated, class-granted first.
     *
     * @return an immutable list; empty, never null
     */
    public static List<String> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resolved = new ArrayList<String>(4);

        Item item = stack.getItem();
        if (item instanceof ITweaksPropertyHolder) {
            List<String> fromClass = ((ITweaksPropertyHolder) item).getActiveAdvProperties(stack);
            if (fromClass != null) {
                for (String id : fromClass) {
                    // gear.properties can switch an individual property off. Filtering here rather
                    // than at each source means one check covers all three of them.
                    if (id != null && !resolved.contains(id) && AdvPropertyRegistry.isEnabled(id)) {
                        resolved.add(id);
                    }
                }
            }
        }

        for (String id : AdvPropertyStorage.read(stack)) {
            if (!resolved.contains(id) && AdvPropertyRegistry.isEnabled(id)) {
                resolved.add(id);
            }
        }

        if (AdvPropertyRegistry.isEnabled(AdvPropertyRegistry.ASHEN_LEGACY)
                && ModConfig.enchantments.sentientCodex.conferAshenLegacy
                && EnchantmentSentientCodex.hasSentientCodex(stack)
                && !resolved.contains(AdvPropertyRegistry.ASHEN_LEGACY)) {
            resolved.add(AdvPropertyRegistry.ASHEN_LEGACY);
        }

        return resolved.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(resolved);
    }

    /** Whether this stack has {@code propertyId} from any source. */
    public static boolean has(ItemStack stack, String propertyId) {
        if (stack == null || stack.isEmpty() || propertyId == null
                || !AdvPropertyRegistry.isEnabled(propertyId)) {
            return false;
        }

        // Ordered cheapest-first: the NBT read is a map lookup, the interface check is an
        // instanceof, and only the enchant test walks a tag list.
        if (AdvPropertyStorage.has(stack, propertyId)) {
            return true;
        }

        Item item = stack.getItem();
        if (item instanceof ITweaksPropertyHolder
                && ((ITweaksPropertyHolder) item).hasAdvProperty(stack, propertyId)) {
            return true;
        }

        return AdvPropertyRegistry.ASHEN_LEGACY.equals(propertyId)
                && ModConfig.enchantments.sentientCodex.conferAshenLegacy
                && EnchantmentSentientCodex.hasSentientCodex(stack);
    }
}
