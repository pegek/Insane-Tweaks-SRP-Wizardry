package com.spege.manacore.api;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.ManaMath;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * The only public entry point for other mods. Every method is safe against a null player and
 * against being called on the client, where writes are ignored rather than desyncing the pool.
 *
 * <p>Every write guards against NaN and infinity. That is not defensive habit: {@code ManaPool}
 * decides whether to send a sync packet by comparing the old and new value, and {@code NaN} never
 * equals itself, so a single poisoned write would turn packet throttling into a packet every tick,
 * forever, with no symptom other than traffic. Beware that a sign test does not catch it - any
 * comparison with NaN is false, so {@code amount <= 0.0D} lets NaN straight through.
 */
public final class ManaAPI {

    private ManaAPI() {
    }

    public static double getMana(@Nullable EntityPlayer player) {
        IManaPool pool = ManaCapabilities.get(player);
        return pool == null ? 0.0D : pool.getCurrent();
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        return ManaAttributes.getMaxMana(player);
    }

    /**
     * Whether the player can afford {@code amount}. Creative players always can, matching
     * {@link #spendQuiet} - gate on this rather than comparing {@link #getMana} yourself, or the
     * gate and the charge will disagree in creative mode.
     */
    public static boolean hasMana(@Nullable EntityPlayer player, double amount) {
        if (player != null && player.capabilities.isCreativeMode) {
            return true;
        }
        return getMana(player) >= amount;
    }

    /** Returns false and changes nothing when the player cannot afford the cost. */
    public static boolean spend(@Nullable EntityPlayer player, double amount) {
        if (!spendQuiet(player, amount)) {
            return false;
        }
        syncNow(player);
        return true;
    }

    /**
     * Subtracts mana like {@link #spend} but sends no packet, leaving the pool marked dirty for
     * the periodic sync in the tick handler to deliver (within half a second).
     *
     * <p>For paths that repeat every tick - above all the upkeep of a channelled spell. Sending
     * unconditionally there would mean one packet per player per tick for as long as the channel
     * lasts. Use plain {@link #spend} for discrete actions, where the player should see the cost
     * land immediately.
     */
    public static boolean spendQuiet(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || !isFinite(amount)) {
            return false;
        }
        // Creative players never pay. Both mods this bridges to exempt them in their own spend
        // paths - Trinkets and Baubles checks isCreativePlayer() inside spendMana, and our
        // handlers replace those paths wholesale - so without this the bridges would be strictly
        // more restrictive in creative than the mods they replace. Reported as success: the
        // caster gets the spell, the pool is simply not touched.
        if (player.capabilities.isCreativeMode) {
            return true;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null || pool.getCurrent() < amount) {
            return false;
        }
        pool.setCurrent(ManaMath.afterSpend(pool.getCurrent(), amount));
        return true;
    }

    public static void add(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || !isFinite(amount) || amount <= 0.0D) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setCurrent(ManaMath.afterRegen(pool.getCurrent(), getMaxMana(player), amount));
        syncNow(player);
    }

    public static void setMana(@Nullable EntityPlayer player, double value) {
        if (player == null || player.world.isRemote || !isFinite(value)) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setCurrent(ManaMath.clamp(value, 0.0D, getMaxMana(player)));
        syncNow(player);
    }

    /** Adds permanent progression, hard-limited by the configured cap. */
    public static void addProgression(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || !isFinite(amount) || amount <= 0.0D) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setProgressionBonus(ManaMath.afterProgressionGain(
                pool.getProgressionBonus(), ManaCoreConfig.pool.progressionCap, amount));
        ManaAttributes.refreshProgressionModifier(player);
        syncNow(player);
    }

    /**
     * Installs or replaces a max-mana modifier keyed by the given UUID. This is the entry point
     * for every future source of bonus max mana - baubles, enchantments, tool quality, skill
     * levels - and the core needs to know nothing else about any of them.
     *
     * <p>The UUID must be a stable constant, never generated per call: vanilla serialises the
     * attribute map with its modifiers into the player's NBT, so a changing id orphans the old
     * modifier in existing worlds and stacks a second one beside it.
     *
     * @param operation 0 adds a flat amount, 1 and 2 are the multiplicative forms.
     */
    public static void addMaxModifier(@Nullable EntityPlayer player, UUID id, String name,
            double amount, int operation) {
        ManaAttributes.applyMaxModifier(player, id, name, amount, operation);
    }

    public static void removeMaxModifier(@Nullable EntityPlayer player, UUID id) {
        ManaAttributes.applyMaxModifier(player, id, "manacore.removed", 0.0D, 0);
    }

    /**
     * Sends UNCONDITIONALLY. Right for discrete actions - drinking a potion, one cast spell, a
     * command - and wrong for anything that repeats every tick. High-frequency callers should use
     * {@link #spendQuiet} and let the periodic sync deliver the value instead.
     */
    private static void syncNow(@Nullable EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            ManaNetwork.syncNow((EntityPlayerMP) player);
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
