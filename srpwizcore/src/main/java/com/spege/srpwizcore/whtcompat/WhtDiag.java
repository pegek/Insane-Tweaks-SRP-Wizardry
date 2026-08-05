package com.spege.srpwizcore.whtcompat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/**
 * Aggregated diagnostics for WorseHurtTimer, written because WHT's own logging cannot answer the
 * questions we have about it.
 *
 * <p>Two things make {@code B:doLogging=true} unusable here: it is all-or-nothing, producing
 * roughly thirty lines per hit (15 MB inside a few minutes of testing), and none of those lines
 * name the <em>target</em> of an attack — only the attacker — so with a crowd of mobs you cannot
 * tell which attacker/target pair a line belongs to. This class counts instead of narrating, and
 * always records the target, which the mixins and the attack listener have in scope anyway.
 *
 * <p>Counters are kept under string keys in one concurrent map. That is deliberately dumb: the
 * whole thing is off by default, only switched on for an investigation, and one map with atomic
 * counters is obviously correct under the pack's threaded entity ticking without anyone having to
 * reason about it.
 *
 * <p>{@link #ENABLED} is a plain volatile boolean so every hot-path call site can bail out on one
 * field read before doing any string work.
 */
public final class WhtDiag {

    /** Hot-path guard. Mirrored from the config; read before any other work. */
    public static volatile boolean ENABLED = false;
    /** Also emit one log line per event, on top of the counters. Sterile tests only. */
    public static volatile boolean VERBOSE = false;
    /** Ignore events whose target is not a player. */
    public static volatile boolean PLAYERS_ONLY = true;

    private static final ConcurrentHashMap<String, AtomicLong> COUNTERS =
            new ConcurrentHashMap<String, AtomicLong>();
    private static volatile long startedAtMillis = 0L;

    /**
     * {@code EntityLivingBase.ticksSinceLastSwing}, resolved once for the {@code canSwing} probe.
     *
     * <p>Looked up ourselves rather than borrowed from {@code BHTAPI.ticksSinceLastSwingField} so
     * this class keeps no link to WorseHurtTimer and stays loadable when the mod is absent. Both
     * the SRG and the MCP name are tried because which one exists depends on the deobfuscation
     * state of the runtime. A null here is not fatal — the probe reports {@code field?} and the
     * other half of the condition is still measured.
     */
    private static final Field SWING_FIELD = resolveSwingField();

    private static Field resolveSwingField() {
        for (String name : new String[] { "field_184617_aD", "ticksSinceLastSwing" }) {
            try {
                Field f = EntityLivingBase.class.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                // wrong mapping for this runtime; try the other spelling
            }
        }
        return null;
    }

    private WhtDiag() {
    }

    /** Mirrors the config values onto the hot-path fields. Called on load and on config change. */
    public static void syncFromConfig(boolean enabled, boolean verbose, boolean playersOnly) {
        if (enabled && !ENABLED) {
            reset();
        }
        ENABLED = enabled;
        VERBOSE = verbose;
        PLAYERS_ONLY = playersOnly;
    }

    public static void reset() {
        COUNTERS.clear();
        startedAtMillis = System.currentTimeMillis();
    }

    private static void inc(String key) {
        AtomicLong c = COUNTERS.get(key);
        if (c == null) {
            c = COUNTERS.putIfAbsent(key, new AtomicLong(1L));
            if (c == null) {
                return;
            }
        }
        c.incrementAndGet();
    }

    private static boolean skip(Entity target) {
        return PLAYERS_ONLY && !(target instanceof EntityPlayer);
    }

    /** Short, stable label for an entity: registry-ish name without the package noise. */
    public static String label(Entity e) {
        if (e == null) {
            return "none";
        }
        if (e instanceof EntityPlayer) {
            return "player";
        }
        String n = e.getClass().getSimpleName();
        return n.startsWith("Entity") && n.length() > 6 ? n.substring(6) : n;
    }

    /**
     * One melee cooldown computed by {@code Events.getHurtTime}. {@code original} is WHT's own
     * number, {@code scaled} is what we returned after applying the i-frame multiplier.
     */
    public static void recordMelee(Entity target, Entity attacker, int original, int scaled) {
        if (skip(target)) {
            return;
        }
        inc("melee|" + label(attacker) + " -> " + label(target) + "|cd=" + original
                + (scaled != original ? "->" + scaled : ""));
        if (VERBOSE) {
            SrpWizCore.LOGGER.info("[srpwizcore][whtdiag] melee {} -> {} cd={}{}",
                    label(attacker), label(target), original,
                    scaled != original ? " scaled=" + scaled : "");
        }
    }

    /**
     * One per-source i-frame window opened by {@code HurtSourceData.trigger()}. {@code waitTime}
     * is the base WHT would use, {@code tick} is the value after our multiplier.
     */
    public static void recordFrames(EntityLivingBase target, String source, int waitTime, int tick) {
        if (skip(target)) {
            return;
        }
        inc("frames|" + source + "|wait=" + waitTime + (tick != waitTime ? "->" + tick : ""));
        if (VERBOSE) {
            SrpWizCore.LOGGER.info("[srpwizcore][whtdiag] frames {} on {} wait={}{}",
                    source, label(target), waitTime,
                    tick != waitTime ? " scaled=" + tick : "");
        }
    }

    /** A damage source seen for the first time this session and absent from WHT's config table. */
    public static void recordUnlistedSource(String source, int seeded) {
        inc("unlisted|" + source + "|seeded=" + seeded);
        SrpWizCore.LOGGER.info("[srpwizcore][whtdiag] damage source '{}' is not in "
                + "betterhurttimer.cfg's damageSource table, seeded with {} ticks", source, seeded);
    }

    /**
     * Outcome of one attack, observed after WorseHurtTimer's own handler has run. This is the row
     * that answers the actual question — whether WHT's limiter ever refuses anything.
     */
    public static void recordAttack(Entity target, Entity attacker, String source, boolean cancelled) {
        if (skip(target)) {
            return;
        }
        inc("attack|" + source + "|attempts");
        if (cancelled) {
            inc("attack|" + source + "|blocked");
        }
        if (VERBOSE) {
            SrpWizCore.LOGGER.info("[srpwizcore][whtdiag] attack {} -> {} src={} {}",
                    label(attacker), label(target), source, cancelled ? "BLOCKED" : "hit");
        }
    }

    /**
     * Outcome of one attack seen on WorseHurtTimer's own {@code PreLivingAttackEvent}.
     *
     * <p>Exists because {@code attack|...|blocked} is structurally blind to non-melee refusals.
     * WHT posts {@code PreLivingAttackEvent} from an {@code @Inject} at the HEAD of
     * {@code EntityLivingBase.attackEntityFrom} — before {@code ForgeHooks.onLivingAttack} —
     * and cancelling it sets that method's return value, so {@code LivingAttackEvent} is never
     * posted at all. A refused non-melee attack therefore reaches <em>neither</em> counter in
     * {@link #recordAttack}, which is why a run can show plenty of attempts and zero blocks and
     * mean nothing. Melee is the opposite case: WHT refuses it on {@code LivingAttackEvent}
     * itself. Both listeners are kept, under different key prefixes, because each sees a half the
     * other cannot.
     *
     * <p>{@code stalled} separates WHT's two posting sites. It is counted rather than folded in so
     * that an attack posted twice shows up as such instead of quietly inflating the attempts.
     */
    public static void recordPreAttack(Entity target, Entity attacker, String source,
            boolean cancelled, boolean stalled) {
        if (skip(target)) {
            return;
        }
        inc("pre|" + source + "|attempts");
        if (cancelled) {
            inc("pre|" + source + "|blocked");
        }
        if (stalled) {
            inc("pre|" + source + "|stalled");
        }
        if (VERBOSE) {
            SrpWizCore.LOGGER.info("[srpwizcore][whtdiag] pre {} -> {} src={} {}{}",
                    label(attacker), label(target), source, cancelled ? "BLOCKED" : "hit",
                    stalled ? " (stalled)" : "");
        }
    }

    /**
     * Reconstructs both halves of WorseHurtTimer's {@code Events.canSwing(attacker)} condition.
     *
     * <p>{@code canSwing} decides whether melee goes through the attack-speed branch
     * ({@code getCoolPeriod}) or the {@code getHurtResistantTime} one, and a 2026-08-05 session
     * measured it as false 314 times out of 314 — which reading the bytecode says should not
     * happen for an attacker holding a sword, since {@code ItemSword} publishes
     * {@code generic.attackSpeed}. Rather than trust either side, this records the two conjuncts
     * separately so the next run says which one actually fails, and for which item.
     *
     * <p>The attribute lookup allocates a Multimap per call, so this is only ever reached behind
     * {@link #ENABLED} — acceptable for an investigation, not something to leave running.
     */
    public static void recordCanSwing(Entity target, Entity attacker) {
        if (skip(target) || !(attacker instanceof EntityLivingBase)) {
            return;
        }
        final EntityLivingBase living = (EntityLivingBase) attacker;
        final ItemStack held = living.getHeldItem(EnumHand.MAIN_HAND);
        final String item = held.isEmpty() || held.getItem().getRegistryName() == null
                ? "empty"
                : held.getItem().getRegistryName().toString();

        String speedAttr;
        try {
            speedAttr = held.getItem()
                    .getAttributeModifiers(EntityEquipmentSlot.MAINHAND, held)
                    .containsKey(SharedMonsterAttributes.ATTACK_SPEED.getName()) ? "yes" : "no";
        } catch (Throwable t) {
            speedAttr = "threw";
        }

        String swing;
        if (SWING_FIELD == null) {
            swing = "field?";
        } else {
            try {
                swing = SWING_FIELD.getInt(living) >= 0 ? "ok" : "neg";
            } catch (Throwable t) {
                swing = "threw";
            }
        }

        inc("canswing|" + item + "|speedAttr=" + speedAttr + "|swingTicks=" + swing);
    }

    /** Renders the counters. Returns one line per entry, already sorted, newest window first. */
    public static List<String> render() {
        List<String> keys = new ArrayList<String>(COUNTERS.keySet());
        Collections.sort(keys);
        List<String> out = new ArrayList<String>(keys.size() + 4);
        long secs = startedAtMillis == 0 ? 0 : (System.currentTimeMillis() - startedAtMillis) / 1000L;
        out.add("=== whtdiag: " + keys.size() + " counters over " + secs + "s ===");
        if (keys.isEmpty()) {
            out.add("(nothing recorded - is whtCompat.diagEnabled on, and is anything hitting a player?)");
            return out;
        }
        for (String k : keys) {
            AtomicLong v = COUNTERS.get(k);
            long n = v == null ? 0L : v.get();
            if (k.endsWith("|blocked")) {
                String attempts = k.substring(0, k.length() - "blocked".length()) + "attempts";
                AtomicLong a = COUNTERS.get(attempts);
                long total = a == null ? 0L : a.get();
                long pct = total == 0 ? 0 : (100L * n) / total;
                out.add(String.format("%-52s %8d  (%d%% of %d)", k, n, pct, total));
            } else {
                out.add(String.format("%-52s %8d", k, n));
            }
        }
        return out;
    }

    /** Writes the counters to the log. */
    public static void dumpToLog() {
        for (String line : render()) {
            SrpWizCore.LOGGER.info("[srpwizcore] {}", line);
        }
    }

    /** Exposed for the command so it can echo into chat as well. */
    public static Map<String, AtomicLong> raw() {
        return COUNTERS;
    }
}
