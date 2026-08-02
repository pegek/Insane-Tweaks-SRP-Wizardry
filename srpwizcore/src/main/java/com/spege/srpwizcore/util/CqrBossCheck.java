package com.spege.srpwizcore.util;

import java.lang.reflect.Field;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.entity.Entity;

import team.cqr.cqrepoured.entity.bases.AbstractEntityCQR;

/**
 * Single source of truth for "is this a CQR boss". Two boss kinds exist (live finding
 * 2026-08-01, Gremlin Underworld):
 *
 * <ul>
 * <li>dedicated boss classes — package {@code team.cqr...entity.boss.*} (no isBoss() exists
 * in CQR bytecode; the package is the only static marker);</li>
 * <li><b>promoted bosses</b> — a REGULAR mob whose structure NBT carries {@code hasBossBar}:
 * {@code readEntityFromNBT} calls {@code enableBossBar()}, which lazily creates the protected
 * {@code bossInfoServer} field. Class-name checks cannot see these — a promoted gremlin got
 * its weapon swapped and did not seal its boss-room chests (user report).</li>
 * </ul>
 *
 * <p>{@code bossInfoServer} is protected cross-package, so it is read via a cached
 * reflective {@link Field} (class reference is a compile-time constant — CQR is compileOnly
 * on the classpath). Boss checks only run on spawn/right-click/death events, so the
 * reflection cost is irrelevant. Timing note for the gear swap: {@code readEntityFromNBT}
 * runs INSIDE {@code spawnEntityFromNBT}, so the field is already set when the RETURN hook
 * fires. Client side the field stays null — promoted-boss detection is server-only, which
 * is fine: every caller acts on the server result.
 */
public final class CqrBossCheck {

    private static Field bossInfoField;
    private static boolean bossInfoFieldResolved;

    private CqrBossCheck() {
    }

    /** True for dedicated boss classes and promoted (hasBossBar) bosses alike. */
    public static boolean isCqrBoss(Entity e) {
        if (e == null) {
            return false;
        }
        String cls = e.getClass().getName();
        if (!cls.startsWith("team.cqr.")) {
            return false;
        }
        if (cls.contains(".entity.boss.")) {
            return true;
        }
        if (!(e instanceof AbstractEntityCQR)) {
            return false;
        }
        Field f = resolveBossInfoField();
        if (f != null) {
            try {
                return f.get(e) != null;
            } catch (IllegalAccessException ignored) {
                // fall through — treat as non-boss rather than break the caller
            }
        }
        return false;
    }

    private static Field resolveBossInfoField() {
        if (!bossInfoFieldResolved) {
            bossInfoFieldResolved = true;
            try {
                Field f = AbstractEntityCQR.class.getDeclaredField("bossInfoServer");
                f.setAccessible(true);
                bossInfoField = f;
            } catch (Throwable t) {
                SrpWizCore.LOGGER.warn(
                        "[srpwizcore] CqrBossCheck: cannot access bossInfoServer ({}) - "
                                + "promoted-boss detection limited to the boss package",
                        t.toString());
            }
        }
        return bossInfoField;
    }
}
