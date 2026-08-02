package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.util.ParasiteEventEntity;
import com.spege.insanetweaks.util.SrpOriginCaptureState;
import com.spege.insanetweaks.util.SrpWizardryAssimilationHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Captures what a mob was before SRP infected it, so the Hourglass of Restoration can put it back.
 *
 * <h3>Why a snapshot is needed at all</h3>
 * SRP keeps the host id only for the forms it has a mapping for ({@code inf_cow} and friends).
 * Anything it does not recognise - a modded mob, an EB Wizardry wizard - becomes an Incomplete Form
 * ({@code EntityInhooM}/{@code EntityInhooS}), and the only surviving clue is the hitbox size. That
 * is why {@code SrpOriginSnapshotHelper.resolveVanillaId} ends in a pig/cow guess: without a
 * snapshot there is genuinely nothing else to go on.
 *
 * <h3>Correcting the record on the old approach</h3>
 * The previous version of this file claimed SRP used {@code func_72838_d} (addEntity) rather than
 * {@code spawnEntity}, and that {@code EntityJoinWorldEvent} therefore never fired. That is wrong:
 * in 1.12.2 {@code func_72838_d} <b>is</b> the SRG name of {@code World.spawnEntity} - one method,
 * and the event fires normally. The real reason the event-only {@code ZhonyasEventHandler} could
 * never find the original is the ordering. {@code javap -p -c} on {@code ParasiteEventEntity}
 * (2026-07-28) shows {@code removeEntity} strictly before {@code spawnEntity} on every branch:
 * {@code convertEntity} 279-&gt;332, 607-&gt;667, 921-&gt;974; {@code spawnInsider} 236-&gt;242.
 * By the time anything can observe the new parasite, the original is already dead, and a handler
 * that skips dead candidates finds nothing. The whole PENDING-map workaround was built on that
 * mis-diagnosis and has been removed.
 *
 * <h3>How it works now</h3>
 * HEAD snapshots the doomed entity into {@link SrpOriginCaptureState}; a {@link Redirect} on the
 * {@code spawnEntity} call stamps that snapshot onto <b>exactly</b> the entity being spawned, then
 * performs the spawn; RETURN clears the thread-local.
 *
 * <p>This replaces a "scan {@code loadedEntityList} backwards for any Inhoo" heuristic, which
 * picked an arbitrary matching parasite whenever more than one was loaded - so the snapshot could
 * land on the wrong mob and restore it into something it never was. The redirect has the reference
 * in hand, so mis-attribution is impossible.
 *
 * <p>The redirect is scoped per method, which is what makes it bind all three of
 * {@code convertEntity}'s alternative spawn branches and {@code spawnInsider}'s single one.
 * Stamping happens <i>before</i> the spawn so the data is present when
 * {@code EntityJoinWorldEvent} fires.
 *
 * <p>Not covered, deliberately: {@code convertEntityFeral} (spawns at 402/534/816) and
 * {@code hijackEntity} (250/364) follow the same remove-then-spawn shape. Their outputs are
 * {@code fer_*} forms that the static host table already maps for vanilla hosts, so they only lose
 * fidelity for modded hosts. Extending to them is a one-line-per-method addition if that turns out
 * to matter.
 */
@Mixin(value = ParasiteEventEntity.class, remap = false)
public abstract class MixinParasiteEventEntity {

    private static final String SPAWN_INSIDER =
            "spawnInsider(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/world/World;"
            + "Lnet/minecraft/nbt/NBTTagCompound;)V";
    private static final String CONVERT_ENTITY =
            "convertEntity(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/nbt/NBTTagCompound;"
            + "Z[Ljava/lang/String;)V";
    private static final String SPAWN_ENTITY =
            "Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z";

    // ── spawnInsider: modded/unknown host -> Incomplete Form ─────────────────────────────────

    @Inject(method = SPAWN_INSIDER, at = @At("HEAD"), remap = false, require = 0)
    private static void insanetweaks$captureForInsider(EntityLivingBase entity, World world,
            NBTTagCompound tags, CallbackInfo ci) {
        if (entity == null || world == null || world.isRemote) {
            return;
        }
        SrpOriginCaptureState.capture(entity);
    }

    @Redirect(method = SPAWN_INSIDER, at = @At(value = "INVOKE", target = SPAWN_ENTITY), remap = false,
            require = 0)
    private static boolean insanetweaks$stampInsider(World world, Entity spawned) {
        SrpOriginCaptureState.stampOnto(spawned);
        return world.spawnEntity(spawned);
    }

    @Inject(method = SPAWN_INSIDER, at = @At("RETURN"), remap = false, require = 0)
    private static void insanetweaks$clearAfterInsider(EntityLivingBase entity, World world,
            NBTTagCompound tags, CallbackInfo ci) {
        SrpOriginCaptureState.clear();
    }

    // ── convertEntity: known host -> assimilated form ────────────────────────────────────────

    @Inject(method = CONVERT_ENTITY, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void insanetweaks$captureForConvert(EntityLivingBase entityin, NBTTagCompound tags,
            boolean ignoreKey, String[] list, CallbackInfo ci) {
        if (entityin == null || entityin.world == null || entityin.world.isRemote) {
            return;
        }
        // The wizard-assimilation bridge takes the entity down its own path entirely; if it claims
        // this conversion there is no SRP spawn to stamp and nothing to snapshot.
        if (SrpWizardryAssimilationHelper.tryConvertSupportedWizard(entityin, tags)) {
            ci.cancel();
            return;
        }
        SrpOriginCaptureState.capture(entityin);
    }

    @Redirect(method = CONVERT_ENTITY, at = @At(value = "INVOKE", target = SPAWN_ENTITY), remap = false,
            require = 0)
    private static boolean insanetweaks$stampConverted(World world, Entity spawned) {
        SrpOriginCaptureState.stampOnto(spawned);
        return world.spawnEntity(spawned);
    }

    @Inject(method = CONVERT_ENTITY, at = @At("RETURN"), remap = false, require = 0)
    private static void insanetweaks$clearAfterConvert(EntityLivingBase entityin, NBTTagCompound tags,
            boolean ignoreKey, String[] list, CallbackInfo ci) {
        SrpOriginCaptureState.clear();
    }
}
