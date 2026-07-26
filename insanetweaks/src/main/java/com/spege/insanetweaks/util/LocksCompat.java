package com.spege.insanetweaks.util;

import java.util.List;
import java.util.stream.Collectors;

import com.spege.insanetweaks.InsaneTweaksMod;

import melonslise.locks.common.capability.ILockableHandler;
import melonslise.locks.common.init.LocksCapabilities;
import melonslise.locks.common.init.LocksDamageSources;
import melonslise.locks.common.init.LocksEnchantments;
import melonslise.locks.common.init.LocksSoundEvents;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksPredicates;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * The ONLY class in the mod that touches melonslise.locks.*. Locks is an optional dependency, so
 * every public method here is guarded by {@link #isLoaded()} and the whole public API speaks in
 * vanilla/primitive types only ({@code int} lockable network IDs, {@link World}, {@link BlockPos},
 * {@link EntityPlayer}).
 *
 * <p>That "no Locks type in a signature or a field" rule is load-bearing, not cosmetic: the JVM
 * resolves a constant-pool class reference lazily, on first execution of the bytecode that names
 * it. Keeping Locks types strictly inside method bodies means a pack without Locks can load and
 * call {@link #isLoaded()} without ever resolving a missing class. Do not "clean this up" by
 * returning a {@code Lockable} or caching one in a field.
 *
 * <p>Consumers: {@link com.spege.insanetweaks.items.AutoLockPickerItem} and its HUD handler.
 *
 * <p>Two behaviours of Locks 3.0.0 that this shim relies on (both verified against the compiled
 * jar, see the design doc):
 * <ul>
 *   <li>{@code LocksEvents.onRightClick} vetoes with {@code setUseBlock(DENY)}, never
 *       {@code setCanceled(true)} — so our {@code Item.onItemUse} still runs on a locked block.</li>
 *   <li>{@code Lock extends Observable} and {@code LockableHandler.update} pushes an
 *       {@code UpdateLockablePacket} to tracking players — so {@link #unlock} needs no packet of
 *       our own.</li>
 * </ul>
 */
public final class LocksCompat {

    /** Locks mod id (matches its @Mod annotation, and its Java package root is the same). */
    public static final String LOCKS_MODID = "locks";

    /** Sentinel returned by {@link #findLockedLockableId} when there is no locked lock. */
    public static final int NO_LOCK = -1;

    private static Boolean loaded;

    private LocksCompat() {
    }

    /**
     * True when the Locks mod is present. Deliberately touches no Locks class, so it is safe to
     * call from anywhere; every other method in this class re-checks it before doing real work.
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = Boolean.valueOf(Loader.isModLoaded(LOCKS_MODID));
        }
        return loaded.booleanValue();
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * Network ID of the first still-locked lockable whose box intersects {@code pos}, or
     * {@link #NO_LOCK}. Works on both sides — Locks syncs lockables to tracking clients.
     */
    public static int findLockedLockableId(World world, BlockPos pos) {
        if (!isLoaded() || world == null || pos == null) {
            return NO_LOCK;
        }
        try {
            List<Lockable> hits = LocksUtil.intersecting(world, pos)
                    .filter(LocksPredicates.LOCKED)
                    .collect(Collectors.toList());
            return hits.isEmpty() ? NO_LOCK : hits.get(0).networkID;
        } catch (Throwable t) {
            logFailure("look up a lockable at " + pos, t);
            return NO_LOCK;
        }
    }

    /** Pin count of the lock ({@code Lock.getLength()}) — our difficulty driver. 0 if unknown. */
    public static int getPinCount(World world, int id) {
        Lockable lockable = get(world, id);
        return lockable == null ? 0 : lockable.lock.getLength();
    }

    /** True when the lockable still exists and is still locked (re-validation mid-channel). */
    public static boolean isStillLocked(World world, int id) {
        Lockable lockable = get(world, id);
        return lockable != null && lockable.lock.isLocked();
    }

    /**
     * True when {@code player} is still within {@code maxDistance} blocks of the lockable's centre.
     * Returns false for an unknown lockable, so callers treat "gone" as "out of range".
     */
    public static boolean isWithinRange(World world, int id, EntityPlayer player, double maxDistance) {
        Lockable lockable = get(world, id);
        if (lockable == null || player == null) {
            return false;
        }
        Vec3d centre = lockable.box.center();
        return player.getDistanceSq(centre.x, centre.y, centre.z) <= maxDistance * maxDistance;
    }

    /**
     * Centre of the lockable's box, or null when it is unknown. {@link Vec3d} is a vanilla type, so
     * handing it back does not breach this class's no-Locks-types-in-signatures rule.
     */
    public static Vec3d getCenter(World world, int id) {
        Lockable lockable = get(world, id);
        return lockable == null ? null : lockable.box.center();
    }

    // ------------------------------------------------------------------
    // Lock enchantments (Locks puts them on the lock ItemStack, not the block)
    // ------------------------------------------------------------------

    /** Complexity level on the lock. Locks gate: a pick works iff {@code strength > lvl * 0.25}. */
    public static int getComplexityLevel(World world, int id) {
        return lockEnchantLevel(world, id, true, false);
    }

    /** Sturdy level on the lock. In Locks it divides pick strength; here it scales durability cost. */
    public static int getSturdyLevel(World world, int id) {
        return lockEnchantLevel(world, id, false, true);
    }

    /** Shocking level on the lock — damage dealt when the player aborts the channel. */
    public static int getShockingLevel(World world, int id) {
        return lockEnchantLevel(world, id, false, false);
    }

    private static int lockEnchantLevel(World world, int id, boolean complexity, boolean sturdy) {
        Lockable lockable = get(world, id);
        if (lockable == null) {
            return 0;
        }
        try {
            Enchantment ench = complexity ? LocksEnchantments.COMPLEXITY
                    : sturdy ? LocksEnchantments.STURDY : LocksEnchantments.SHOCKING;
            if (ench == null) {
                return 0;
            }
            return EnchantmentHelper.getEnchantmentLevel(ench, lockable.stack);
        } catch (Throwable t) {
            logFailure("read a lock enchantment level for lockable " + id, t);
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Mutation + feedback
    // ------------------------------------------------------------------

    /**
     * Opens the lock, exactly as turning a key would ({@code Lock.setLocked(false)}). The lock
     * itself stays attached to the block. Server side only.
     *
     * <p>No packet needed: {@code Lock} is an {@code Observable} observed by
     * {@code LockableHandler}, which pushes an {@code UpdateLockablePacket} to tracking players.
     *
     * @return true when a locked lock was actually opened.
     */
    public static boolean unlock(World world, int id) {
        Lockable lockable = get(world, id);
        if (lockable == null || !lockable.lock.isLocked()) {
            return false;
        }
        try {
            lockable.lock.setLocked(false);
            return true;
        } catch (Throwable t) {
            logFailure("unlock lockable " + id, t);
            return false;
        }
    }

    /** Deals Locks' own SHOCK damage — used when a Shocking-enchanted lock's channel is aborted. */
    public static void shock(World world, EntityPlayer player, int id, float damage) {
        if (!isLoaded() || player == null || damage <= 0.0F) {
            return;
        }
        try {
            player.attackEntityFrom(LocksDamageSources.SHOCK, damage);
            playSound(world, id, LocksSoundEvents.SHOCK, 1.0F);
        } catch (Throwable t) {
            logFailure("shock a player off lockable " + id, t);
        }
    }

    /** Locks' "key turned in lock" sound at the lockable's centre. */
    public static void playLockOpen(World world, int id) {
        playSound(world, id, LocksSoundEvents.LOCK_OPEN, 1.0F);
    }

    /** Locks' "lock rattles" sound — the per-second tick of an in-progress channel. */
    public static void playRattle(World world, int id) {
        playSound(world, id, LocksSoundEvents.LOCK_RATTLE, 0.6F);
    }

    private static void playSound(World world, int id, SoundEvent sound, float volume) {
        if (!isLoaded() || world == null || sound == null) {
            return;
        }
        Lockable lockable = get(world, id);
        if (lockable == null) {
            return;
        }
        try {
            Vec3d centre = lockable.box.center();
            world.playSound(null, centre.x, centre.y, centre.z, sound, SoundCategory.BLOCKS,
                    volume, 0.9F + world.rand.nextFloat() * 0.2F);
        } catch (Throwable t) {
            logFailure("play a sound at lockable " + id, t);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Resolves a loaded lockable by network ID, or null. Never leaks the type to callers. */
    private static Lockable get(World world, int id) {
        if (!isLoaded() || world == null || id < 0) {
            return null;
        }
        try {
            ILockableHandler handler = world.getCapability(LocksCapabilities.LOCKABLE_HANDLER, null);
            return handler == null ? null : handler.getLoaded().get(id);
        } catch (Throwable t) {
            logFailure("resolve lockable " + id, t);
            return null;
        }
    }

    private static void logFailure(String what, Throwable t) {
        InsaneTweaksMod.LOGGER.debug("[InsaneTweaks] LocksCompat failed to {} - {}", what, t.toString());
    }
}
