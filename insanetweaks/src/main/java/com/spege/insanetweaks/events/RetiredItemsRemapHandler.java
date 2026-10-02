package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Items this mod used to register and no longer does.
 *
 * <p>🚨 Forge does not quietly forget a registry entry that disappears. An id still referenced by a
 * saved world - an item sitting in a chest, in an inventory, in an item frame - produces the
 * missing-registry screen on load, and the player is offered the choice between losing the world's
 * registry mapping and not playing. A retired item therefore needs a mapping decision recorded here
 * for as long as any world might still contain one, which in practice means forever.
 *
 * <p>{@code ignore()} rather than {@code remap()}: there is nothing to redirect these to.
 * {@code ignore()} drops the stack and leaves the numeric slot dead without shifting any other id.
 *
 * <p>Distinct from {@link LegacyDormantRemapHandler}, which is one specific block's migration to
 * another mod and remaps rather than ignores.
 *
 * <h3>Retired</h3>
 * <ul>
 * <li>{@code arcane_adapted_fruit} - the Arcane Adapted Fruit, retired with the whole player_mana
 * integration in 1.18.0. It granted a mana-regeneration bonus in a mod the pack no longer runs.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public final class RetiredItemsRemapHandler {

    private static final String[] RETIRED_PATHS = {
            "arcane_adapted_fruit"
    };

    private RetiredItemsRemapHandler() {
    }

    @SubscribeEvent
    public static void onMissingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getMappings()) {
            for (String retired : RETIRED_PATHS) {
                if (retired.equals(mapping.key.getResourcePath())) {
                    mapping.ignore();
                    InsaneTweaksMod.LOGGER.info(
                            "[InsaneTweaks] Dropping retired item '{}' from this world's registry.",
                            mapping.key);
                    break;
                }
            }
        }
    }
}
