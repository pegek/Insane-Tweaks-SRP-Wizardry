package com.spege.insanetweaks.util;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import electroblob.wizardry.util.MagicDamage;

/**
 * Server-side bookkeeping and cap-bypassing damage application for the Ray of Purification vs SRP
 * Beckon interaction (spec 2). All state and mutation here is server-authoritative; callers must
 * guard {@code world.isRemote} before invoking (the mixin does).
 *
 * <p><b>Once-per-cast:</b> a Beckon takes the extra hit the FIRST time it is struck during a single
 * continuous cast of the ray. {@code ticksInUse} rises by 1 per game tick within one channel and
 * resets toward 0 on release/re-cast, so a drop signals a fresh cast and clears the per-cast set.
 *
 * <p><b>Cap bypass:</b> SRP's {@code EntityParasiteBase.attackEntityFrom} clamps incoming damage to
 * {@code maxHealth/damageCap} via {@code Math.min(amount, cap)} regardless of an absolute /
 * armor-bypassing DamageSource, so a normal magic hit cannot land the full 20–80. We instead reduce
 * health directly and only route the killing blow through {@code attackEntityFrom} (from 1 HP, where
 * even a capped hit is lethal) so the caster still gets kill credit, loot and death handling.
 */
public final class RayBeckonPurge {

    /** Per-caster continuous-cast state. Weak keys → dead/unloaded casters are GC'd, no pruning. */
    private static final WeakHashMap<EntityLivingBase, CastState> CASTS =
            new WeakHashMap<EntityLivingBase, CastState>();

    private static final float BASE_DAMAGE  = 20.0F;
    private static final float POTENCY_FLOOR = 1.0F;
    private static final float POTENCY_CEIL  = 1.2F;
    private static final float POTENCY_SCALE = 300.0F;

    private RayBeckonPurge() {
    }

    private static final class CastState {
        int lastTicksInUse = Integer.MIN_VALUE;
        final Set<Integer> procced = new HashSet<Integer>();
    }

    /**
     * Records a Beckon hit for {@code caster} at {@code ticksInUse} and returns {@code true} exactly
     * once per continuous cast per Beckon. Server thread only.
     */
    public static boolean shouldProc(EntityLivingBase caster, Entity beckon, int ticksInUse) {
        CastState state = CASTS.get(caster);
        if (state == null) {
            state = new CastState();
            CASTS.put(caster, state);
        }
        if (ticksInUse <= state.lastTicksInUse) {
            // ticksInUse dropped → a new continuous cast started → reset this cast's proc set.
            // NOTE: this reset relies on SpellRay hitting exactly ONE entity per tick (non-piercing);
            // if the ray ever became piercing/AoE this would need per-target tick tracking.
            state.procced.clear();
        }
        state.lastTicksInUse = ticksInUse;
        return state.procced.add(Integer.valueOf(beckon.getEntityId()));
    }

    /** Spec formula: 20 at potency ≤ 1.0, 80 at potency ≥ 1.2, linear between. */
    public static float purgeDamage(float potency) {
        float clamped = Math.max(POTENCY_FLOOR, Math.min(POTENCY_CEIL, potency));
        return BASE_DAMAGE + (clamped - POTENCY_FLOOR) * POTENCY_SCALE;
    }

    /**
     * Applies the unavoidable purge damage to {@code beckon}. Non-lethal portion is a direct
     * setHealth reduction with vanilla hurt feedback (entity-state byte 2 = "entity hurt"); the
     * lethal portion drops the Beckon to 1 HP and delivers a real RADIANT magic {@code
     * attackEntityFrom} so the {@code caster} gets kill credit, loot and correct death handling.
     * Server thread only.
     */
    public static void applyPurge(World world, EntityLivingBase caster, EntityLivingBase beckon, float potency) {
        if (world.isRemote) {
            return;
        }
        float dmg = purgeDamage(potency);
        float hp = beckon.getHealth();
        if (hp - dmg > 0.0F) {
            // Deliberately bypasses SRP's on-hurt retaliation (e.g. EntityPStationaryArchitect bomb
            // spawn) — the "unavoidable" hit is retaliation-free by design; EBW's normal ray ticks
            // still trigger it.
            beckon.setHealth(hp - dmg);
            // Byte 2 = "entity hurt": red flash + hurt sound on clients. Same channel SRP itself
            // uses (EntityParasiteBase.attackEntityAsMobMinimum -> world.setEntityState(target,(byte)2)).
            world.setEntityState(beckon, (byte) 2);
        } else {
            // Drop to 1 HP so even the cap-clamped magic hit is guaranteed lethal, then route the
            // kill through vanilla for proper credit / loot / COTH handling.
            beckon.setHealth(1.0F);
            DamageSource src = MagicDamage.causeDirectMagicDamage(caster, MagicDamage.DamageType.RADIANT);
            beckon.attackEntityFrom(src, Float.MAX_VALUE);
            if (beckon.isEntityAlive()) {
                // disloBurningDeath gene zeroes non-fire magic; OUT_OF_WORLD bypasses both the
                // gene and the damage cap (SRP itself force-kills Beckons with it). Kill
                // attribution is lost only in this rare config-gated case.
                beckon.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            }
        }
    }
}
