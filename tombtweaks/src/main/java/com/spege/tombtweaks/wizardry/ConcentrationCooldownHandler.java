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
 * unless {@code wandsMustBeHeldToDecrementCooldown} is off. On a pack that leaves it on, a wand put
 * away never cools at all. Each level of Concentration gives back a share of that rate.
 *
 * <p>No mixin: {@code WandHelper} is public and static throughout. We work <i>beside</i> Wizardry's
 * path rather than patching it — it still declines to cool a stowed wand, and its behaviour with
 * the setting off, or in the off hand, is left exactly as the author wrote it.
 *
 * <p>🚨 Scope is the player's <b>main inventory only</b>. A wand in a Baubles slot, a backpack
 * mod's container or a shulker box is never reached. Widening that is a decision nobody has taken,
 * not an oversight to quietly fix.
 *
 * <h3>Why the carry</h3>
 * A cooldown moves in whole ticks, so a fractional rate has to become "how many ticks per scan".
 * 🚨 {@code PerkConcentration.getLevelMax()} is <b>2</b>, not the 5 most Tombstone perks allow
 * (verified with javap; {@code getPerkLevelWithBonus} clamps bonus levels to that same cap), so at
 * the shipped defaults — 10% per level, a 10-tick scan — the figure comes out as exactly 1.0 or
 * 2.0 and the carry always stores zero. It earns its place at any percentage that does not divide
 * evenly: at 7% per level a scan is owed 0.7 ticks, and dropping that remainder every time would
 * round the entire rate away to nothing.
 *
 * <p>Server side only. Cooldowns live in stack NBT, the server copy is authoritative, and a
 * player's own container resyncs changed stacks every tick — so a stowed wand is correct by the
 * time it is drawn.
 */
public class ConcentrationCooldownHandler {

    /**
     * Fractional cooldown ticks owed to each player, carried between scans. One entry per online
     * player, dropped on logout.
     *
     * <p>🚨 A plain {@code HashMap} is safe here only because every access below sits under the
     * {@code world.isRemote} return: in single player {@code PlayerTickEvent} is posted on the
     * client thread as well. Hoisting anything above that check would make this map concurrent.
     */
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
        // nothing to give back — granting anything here would double-cool a held wand.
        if (!Wizardry.settings.wandsMustBeHeldToDecrementCooldown) {
            return;
        }

        // Offset by the player so scans spread across the interval instead of all landing on one
        // tick. getTotalWorldTime() is shared by every dimension — WorldServerMulti wraps the
        // overworld's info in DerivedWorldInfo — so without the offset the whole server scans
        // together.
        UUID id = player.getUniqueID();
        int interval = Math.max(1, cfg.scanIntervalTicks);
        if ((player.world.getTotalWorldTime() + (id.hashCode() & 0x7FFFFFFF)) % interval != 0L) {
            return;
        }

        // Cheapest checks first, then the capability lookup: a player without the perk costs one
        // of these per interval and never reaches the loop.
        int level = EntityHelper.getPerkLevelWithBonus(player, ModPerks.concentration);
        if (level <= 0) {
            return;
        }

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
            // The selected slot is what Wizardry means by isSelected, and it cools that itself.
            // The off hand lives in a different list entirely and is never walked here.
            if (slot == selected) {
                continue;
            }
            ItemStack stack = main.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
                continue;
            }
            coolStowedWand(stack, steps);
        }
    }

    /**
     * Take {@code steps} ticks off every spell cooldown on this wand, floored at zero.
     *
     * <p>Deliberately not {@code steps} calls to {@code WandHelper.decrementCooldowns}. That method
     * has no early-out: it reads the NBT array, walks it and writes a fresh {@code NBTTagIntArray}
     * back on every call, even once every cooldown has already reached zero. At the config maxima
     * that is 200 rewrites per wand per scan across up to 36 slots. One pass is identical in
     * result, because subtracting one with a floor, N times, is subtracting N with a floor.
     */
    private static void coolStowedWand(ItemStack stack, int steps) {
        int[] cooldowns = WandHelper.getCooldowns(stack);
        boolean changed = false;
        for (int i = 0; i < cooldowns.length; i++) {
            if (cooldowns[i] > 0) {
                cooldowns[i] = Math.max(0, cooldowns[i] - steps);
                changed = true;
            }
        }
        if (changed) {
            WandHelper.setCooldowns(stack, cooldowns);
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
