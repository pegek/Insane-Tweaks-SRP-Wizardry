package com.spege.tombtweaks.wizardry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.ConcentrationCooldownConfig;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.util.WandHelper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import ovh.corail.tombstone.helper.EntityHelper;
import ovh.corail.tombstone.registry.ModPerks;

/**
 * Concentration cools the wands you are carrying but not holding.
 *
 * <p>Wizardry's {@code ItemWand.onUpdate} decrements a wand's spell cooldowns every tick, but only
 * when the wand is held — {@code isSelected || areItemStacksEqual(stack, getHeldItemOffhand())} —
 * unless {@code wandsMustBeHeldToDecrementCooldown} is off. On a pack that leaves it on, a wand in
 * the backpack never cools at all. Each level of Concentration gives back a share of that rate.
 *
 * <p>No mixin: {@code WandHelper.decrementCooldowns} is public static and is the very method
 * Wizardry calls. We add ticks <i>beside</i> its path rather than patching it — Wizardry still
 * declines to cool a stowed wand, and its behaviour with the setting off, or in the off hand, is
 * left exactly as the author wrote it.
 *
 * <h3>Why the carry</h3>
 * {@code decrementCooldowns} removes exactly one tick per call, so a fractional rate has to become
 * "how many calls per scan". Doing that by calling once every {@code 100 / percent} ticks collapses
 * levels onto the same interval — at 10% per level, levels 4 and 5 both round to every other tick,
 * and the fifth level of the perk buys the player nothing. Keeping the remainder makes every level
 * distinct and honours any configured percentage exactly.
 *
 * <p>Server side only. Cooldowns live in stack NBT, the server copy is authoritative, and a
 * player's own container resyncs changed stacks every tick — so a stowed wand is correct by the
 * time it is drawn.
 */
public class ConcentrationCooldownHandler {

    /** Fractional cooldown ticks owed to each player, carried between scans. Bounded by players online. */
    private final Map<UUID, Double> carry = new HashMap<UUID, Double>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }

        ConcentrationCooldownConfig cfg = TombTweaksConfig.tombstone.concentrationCooldown;
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !cfg.enabled) {
            return;
        }
        if (cfg.percentPerLevel <= 0) {
            return;
        }

        // Wizardry's own rule. With this off, cooldowns already run everywhere and there is
        // nothing to give back — granting anything here would double-tick a held wand.
        if (!Wizardry.settings.wandsMustBeHeldToDecrementCooldown) {
            return;
        }

        int interval = Math.max(1, cfg.scanIntervalTicks);
        if (player.world.getTotalWorldTime() % interval != 0L) {
            return;
        }

        // Cheapest checks first, then the capability lookup: a player without the perk costs one
        // of these per interval and never reaches the loop.
        int level = EntityHelper.getPerkLevelWithBonus(player, ModPerks.concentration);
        if (level <= 0) {
            return;
        }

        UUID id = player.getUniqueID();
        double owed = (level * cfg.percentPerLevel / 100.0D) * interval;
        Double pending = carry.get(id);
        if (pending != null) {
            owed += pending.doubleValue();
        }
        int steps = (int) owed;
        carry.put(id, Double.valueOf(owed - steps));
        if (steps <= 0) {
            return;
        }

        NonNullList<ItemStack> main = player.inventory.mainInventory;
        int selected = player.inventory.currentItem;
        for (int slot = 0; slot < main.size(); slot++) {
            // The selected slot is what Wizardry means by isSelected, and it cools it itself.
            // The off hand lives in a different list entirely and is never walked here.
            if (slot == selected) {
                continue;
            }
            ItemStack stack = main.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
                continue;
            }
            for (int i = 0; i < steps; i++) {
                WandHelper.decrementCooldowns(stack);
            }
        }
    }

    /** Drop the carry so the map cannot outlive the session. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            carry.remove(event.player.getUniqueID());
        }
    }
}
