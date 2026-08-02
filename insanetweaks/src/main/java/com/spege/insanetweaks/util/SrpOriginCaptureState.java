package com.spege.insanetweaks.util;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.SrpOriginSnapshotHelper.OriginalSnapshot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * Carries the pre-infection snapshot of an entity across a single SRP conversion call.
 *
 * <p>Lives outside {@code com.spege.insanetweaks.mixins.*} on purpose, for two independent
 * reasons. Referencing a helper type from inside the mixin package throws
 * {@code IllegalClassLoadError}, and a mixin that declares no static fields has no {@code <clinit>}
 * for Mixin to merge into the target - which is the whole class of load-time verification failure
 * CLAUDE.md warns about (it cost {@code MixinSrpSaveDataGetRace} a crash). Keeping the
 * {@link ThreadLocal} here means {@code MixinParasiteEventEntity} holds no state at all.
 *
 * <p>{@code ThreadLocal} rather than a shared map because SRP runs conversions on EntityThreading
 * worker threads; the snapshot only has to survive from the method's HEAD to its
 * {@code World.spawnEntity} call on the same thread.
 */
public final class SrpOriginCaptureState {

    private static final ThreadLocal<OriginalSnapshot> CAPTURE = new ThreadLocal<OriginalSnapshot>();
    private static final ThreadLocal<Boolean> STAMPED = new ThreadLocal<Boolean>();

    /** How many orphan warnings are still worth emitting before they become noise. */
    private static int orphanWarningsLeft = 3;

    private SrpOriginCaptureState() {
    }

    /** Snapshots {@code entity} as it is right now and arms it for the next spawn on this thread. */
    public static void capture(EntityLivingBase entity) {
        CAPTURE.set(snapshot(entity));
        STAMPED.set(Boolean.FALSE);
    }

    /**
     * Drops any armed snapshot. Must run on every exit path or the next conversion inherits it.
     *
     * <p>Also reports the one failure mode that would otherwise be invisible. These injections run
     * at {@code require = 0} - deliberately, until a live {@code cleanmix.log} confirms they bind -
     * and a {@code @Redirect} that fails to bind under {@code require = 0} is a silent no-op that
     * looks exactly like everything working. So if a conversion armed a snapshot and finished
     * without the spawn redirect ever consuming it, say so out loud: that is the signal to raise
     * these to {@code require = 1}, or evidence that SRP moved the call.
     */
    public static void clear() {
        OriginalSnapshot armed = CAPTURE.get();
        boolean stamped = Boolean.TRUE.equals(STAMPED.get());
        CAPTURE.remove();
        STAMPED.remove();

        if (armed != null && !stamped && orphanWarningsLeft > 0) {
            orphanWarningsLeft--;
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][Restoration] Snapshot of '{}' was captured but no SRP spawn consumed it - "
                    + "the World.spawnEntity redirect in MixinParasiteEventEntity did not fire. Restoring this "
                    + "parasite will fall back to a guess. Check cleanmix.log for the mixin's APPLY line.{}",
                    armed.resourceId,
                    orphanWarningsLeft == 0 ? " (further warnings suppressed)" : "");
        }
    }

    /**
     * Writes the armed snapshot onto {@code target}'s persistent entity data.
     *
     * <p>Callers stamp <b>before</b> handing the entity to {@code World.spawnEntity}, so the data is
     * already in place when {@code EntityJoinWorldEvent} fires and when the entity is first
     * serialised.
     *
     * @return true if a snapshot was armed and applied
     */
    public static boolean stampOnto(Entity target) {
        OriginalSnapshot capture = CAPTURE.get();
        if (capture == null || target == null) {
            return false;
        }
        NBTTagCompound data = target.getEntityData();
        data.setString(SrpOriginSnapshotHelper.KEY_ORIGINAL_ID, capture.resourceId);
        data.setTag(SrpOriginSnapshotHelper.KEY_ORIGINAL_NBT, capture.fullNbt.copy());
        STAMPED.set(Boolean.TRUE);
        debug("stamped '{}' onto {}", capture.resourceId, target.getClass().getSimpleName());
        return true;
    }

    /**
     * Full NBT dump of the entity, sanitised of death state and of the infection that is about to
     * happen, so restoring it later yields a healthy mob rather than a dying infected one.
     */
    @Nullable
    private static OriginalSnapshot snapshot(EntityLivingBase entity) {
        if (entity == null) {
            return null;
        }
        try {
            ResourceLocation id = EntityList.getKey(entity);
            if (id == null) {
                debug("no registry key for {} - not snapshotting", entity.getClass().getSimpleName());
                return null;
            }
            NBTTagCompound nbt = new NBTTagCompound();
            entity.writeToNBT(nbt);
            nbt.removeTag("DeathTime");
            nbt.removeTag("HurtTime");
            nbt.removeTag("FallDistance");
            nbt.removeTag("srpcothimmunity");
            nbt.setFloat("Health", entity.getMaxHealth() * 0.75f);
            stripSrpEffects(nbt);
            return new OriginalSnapshot(id.toString(), nbt, System.currentTimeMillis());
        } catch (Exception ex) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks][Restoration] Snapshot of {} failed: {}",
                    entity.getClass().getSimpleName(), ex.toString());
            return null;
        }
    }

    /**
     * Drops SRP's own status effects from a serialised effect list. Potion ids at or above 100 are
     * SRP's range (vanilla occupies 1-32 and mods typically stay below 100); leaving them in would
     * restore the mob still carrying its infection effects.
     */
    private static void stripSrpEffects(NBTTagCompound nbt) {
        if (!nbt.hasKey("ActiveEffects", 9)) {
            return;
        }
        NBTTagList effects = nbt.getTagList("ActiveEffects", 10);
        NBTTagList cleaned = new NBTTagList();
        for (int i = 0; i < effects.tagCount(); i++) {
            NBTTagCompound effect = effects.getCompoundTagAt(i);
            if ((effect.getByte("Id") & 0xFF) < 100) {
                cleaned.appendTag(effect);
            }
        }
        nbt.setTag("ActiveEffects", cleaned);
    }

    /**
     * Restoration diagnostics. Gated because every line here sits on a per-entity-join path, which
     * on a parasite-heavy pack means thousands of lines a minute at INFO.
     */
    public static void debug(String message, Object... args) {
        if (ModConfig.tweaks.restorationDebugLogging) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks][Restoration] " + message, args);
        }
    }
}
