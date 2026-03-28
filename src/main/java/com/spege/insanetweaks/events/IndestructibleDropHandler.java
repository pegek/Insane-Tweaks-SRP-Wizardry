package com.spege.insanetweaks.events;

import com.spege.insanetweaks.entities.EntityItemIndestructible;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Replaces standard EntityItem drops for Living/Sentient items with
 * EntityItemIndestructible, making them immune to fire, lava, and explosions.
 *
 * Uses EntityJoinWorldEvent (server-side only) — intercepts the item entity
 * before it is ticked for the first time, cancels the vanilla entity, and
 * spawns our indestructible replacement at the same position.
 *
 * Registered unconditionally (not gated behind enableSrpEbWizardryBridge)
 * because the effect is cosmetic/protective and requires no mod-specific classes.
 */
public class IndestructibleDropHandler {

    /** All Living/Sentient item registry names that should be fire-resistant when dropped. */
    private static final Set<String> INDESTRUCTIBLE_ITEMS = new HashSet<>(Arrays.asList(
        // Armor — Living (pre-evolution)
        "insanetweaks:parasite_mage_helmet",
        "insanetweaks:parasite_mage_chestplate",
        "insanetweaks:parasite_mage_leggings",
        "insanetweaks:parasite_mage_boots",
        // Armor — Sentient (post-evolution)
        "insanetweaks:battle_mage_helmet",
        "insanetweaks:battle_mage_chestplate",
        "insanetweaks:battle_mage_leggings",
        "insanetweaks:battle_mage_boots",
        // Spellblades
        "insanetweaks:living_spellblade",
        "insanetweaks:sentient_spellblade",
        // Aegis shields
        "insanetweaks:living_aegis",
        "insanetweaks:sentient_aegis"
    ));

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        // Server-side only — client world does not handle item destruction
        if (event.getWorld().isRemote) return;

        // Only interested in vanilla EntityItem drops — skip our own class
        if (!(event.getEntity() instanceof EntityItem)
                || event.getEntity() instanceof EntityItemIndestructible) return;

        EntityItem dropped = (EntityItem) event.getEntity();
        ItemStack stack = dropped.getItem();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        ResourceLocation regName = item.getRegistryName();
        if (regName == null) return;

        if (!INDESTRUCTIBLE_ITEMS.contains(regName.toString())) return;

        // Cancel the vanilla drop and replace with our indestructible entity
        event.setCanceled(true);

        EntityItemIndestructible fireproof = new EntityItemIndestructible(
            event.getWorld(),
            dropped.posX,
            dropped.posY,
            dropped.posZ,
            stack
        );

        // Preserve velocity from the original entity
        fireproof.motionX = dropped.motionX;
        fireproof.motionY = dropped.motionY;
        fireproof.motionZ = dropped.motionZ;
        // setPickupDelay is the only safe public API in 1.12.2 — use the vanilla default (20 ticks)
        fireproof.setPickupDelay(20);

        event.getWorld().spawnEntity(fireproof);
    }
}
