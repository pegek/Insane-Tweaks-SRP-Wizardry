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

    /**
     * Adds permanent progression from spellcasting, hard-limited by
     * {@code ManaCoreConfig.pool.castProgressionCap}.
     *
     * <p>There are two progression-adding methods, {@link #addCastProgression} and
     * {@link #addItemProgression}, rather than one, because they draw from two independent
     * budgets with two independent caps ({@code pool.castProgressionCap} and
     * {@code pool.itemProgressionCap}). A single method with a single cap would let either source
     * push the total past whichever cap looked at it last - which is exactly the bug this split
     * fixes: before it, casting and eating a Mana Crystal shared one field and two unrelated
     * ceilings, so neither ceiling was actually a ceiling on the total.
     */
    public static void addCastProgression(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || !isFinite(amount) || amount <= 0.0D) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setCastProgression(ManaMath.afterProgressionGain(
                pool.getCastProgression(), ManaCoreConfig.pool.castProgressionCap, amount));
        ManaAttributes.refreshPersistentModifiers(player);
        syncNow(player);
    }

    /**
     * Adds permanent progression from consumed items (Trinkets and Baubles' Mana Crystal and
     * anything else that grants permanent maximum through this method), hard-limited by
     * {@code ManaCoreConfig.pool.itemProgressionCap}.
     *
     * <p>See {@link #addCastProgression} for why this is a separate method with a separate cap
     * rather than one shared budget.
     */
    public static void addItemProgression(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || !isFinite(amount) || amount <= 0.0D) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setItemProgression(ManaMath.afterProgressionGain(
                pool.getItemProgression(), ManaCoreConfig.pool.itemProgressionCap, amount));
        ManaAttributes.refreshPersistentModifiers(player);
        syncNow(player);
    }

    /**
     * The persistent half of the maximum only - config base, progression, flat grants - without
     * whatever the player happens to be wearing. Use {@link #getMaxMana} for the number that
     * actually gates casting; this is for code that needs to reason about the two halves apart,
     * such as a command that sets a target and must not count gear towards it.
     */
    public static double getPersistentMaxMana(@Nullable EntityPlayer player) {
        return ManaAttributes.getPersistentMaxMana(player);
    }

    /** The dynamic half of the maximum only: everything granted by currently worn gear. */
    public static double getBonusMana(@Nullable EntityPlayer player) {
        return ManaAttributes.getBonusMana(player);
    }

    /** Flat maximum granted outright, outside both progression budgets. Survives death. */
    public static double getGrantedMax(@Nullable EntityPlayer player) {
        IManaPool pool = ManaCapabilities.get(player);
        return pool == null ? 0.0D : pool.getGrantedMax();
    }

    /**
     * Adds to the flat granted maximum: a permanent award that is neither progression nor gear -
     * an achievement, a quest reward, a one-off from a command. Unlike the progression methods
     * this has no cap of its own, and unlike a bare attribute modifier it survives death, because
     * the value is stored in the capability and re-applied on respawn.
     *
     * <p>🚨 This is ONE shared number, not a per-source ledger. Two sources that both add here can
     * never be told apart afterwards, so a source whose value can CHANGE - a skill level, a
     * difficulty setting, anything recomputed - must not use this: it would have no way to
     * subtract its own previous contribution. Such a source owns an attribute modifier with its
     * own UUID instead, via {@link #addMaxModifier}, re-applied whenever its input changes.
     * Only genuinely one-way, one-shot awards belong here.
     *
     * <p>Negative amounts are allowed, so an award can be revoked; the total is floored at zero.
     */
    public static void addGrantedMax(@Nullable EntityPlayer player, double amount) {
        if (!isFinite(amount) || amount == 0.0D) {
            return;
        }
        setGrantedMax(player, getGrantedMax(player) + amount);
    }

    /** Sets the flat granted maximum to an absolute value. See {@link #addGrantedMax}. */
    public static void setGrantedMax(@Nullable EntityPlayer player, double value) {
        if (player == null || player.world.isRemote || !isFinite(value)) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setGrantedMax(value < 0.0D ? 0.0D : value);
        ManaAttributes.refreshPersistentModifiers(player);
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
     * Installs or replaces a DYNAMIC bonus-mana modifier: maximum mana granted only while its
     * source is active, which for this pack means worn gear above all - EBW and Trinkets and
     * Baubles artifacts. Call again with an amount of zero (or {@link #removeBonusModifier}) when
     * the item comes off, and the player's maximum drops the same tick.
     *
     * <p>Differs from {@link #addMaxModifier} in lifetime, not in arithmetic: these modifiers are
     * never written to the player's save and never survive death, on the assumption that whatever
     * equips the item applies them again. That assumption is what makes them safe - a saved gear
     * bonus would outlive the gear with nothing able to notice.
     *
     * <p>The UUID must still be a stable per-source constant, so re-applying replaces rather than
     * stacks; it just does not have to be unique across saves the way a persistent one does.
     *
     * @param operation 0 adds a flat amount, 1 and 2 are the multiplicative forms.
     */
    public static void addBonusModifier(@Nullable EntityPlayer player, UUID id, String name,
            double amount, int operation) {
        ManaAttributes.applyBonusModifier(player, id, name, amount, operation);
    }

    public static void removeBonusModifier(@Nullable EntityPlayer player, UUID id) {
        ManaAttributes.applyBonusModifier(player, id, "manacore.removed", 0.0D, 0);
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
